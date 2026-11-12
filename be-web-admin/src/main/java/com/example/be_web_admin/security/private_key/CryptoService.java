package com.example.be_web_admin.security.private_key;

import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Mã hóa / giải mã secret TOTP bằng AES-256-GCM.
 *
 * Thiết kế:
 *  - Master key đọc từ cấu hình (biến môi trường), theo phiên bản để xoay khóa.
 *  - Mỗi lần mã hóa sinh salt 16 byte mới, rồi dẫn xuất khóa AES bằng HKDF-SHA256 (RFC 5869)
 *    từ (master key, salt). Mỗi bản ghi dùng một khóa AES riêng.
 *  - AAD = "totp:{adminId}:v{keyVersion}": ciphertext của admin A chép sang admin B sẽ giải mã thất bại.
 *  - Bố cục lưu trữ: salt | iv | ciphertext+tag (khớp các cột kdf_salt, iv, secret_cipher).
 */
@Component
public class CryptoService {

    // ---- Hằng số ----
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BYTE = 16;
    private static final int TAG_LENGTH_BIT = TAG_LENGTH_BYTE * 8;
    private static final int MIN_MASTER_KEY_BYTES = 32;

    /**
     * Độ dài khóa TOTP (byte). 20 byte -> Base32 32 ký tự -> ciphertext 32 + 16 = 48 byte, vừa varbinary(64).
     * Nếu đổi thành 32 byte: chuỗi Base32 dài 52 ký tự -> ciphertext 68 byte, KHÔNG vừa varbinary(64),
     * phải ALTER cột secret_cipher (ví dụ varbinary(96)) và sửa giới hạn trong EnrollmentServiceImpl.
     */
    public static final int TOTP_SECRET_BYTES = 20;

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String HMAC = "HmacSHA256";
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    // SecureRandom an toàn khi dùng chung giữa nhiều luồng.
    private static final SecureRandom RNG = new SecureRandom();

    private final int activeVersion;
    private final Map<Integer, byte[]> masterKeys;
    private final String legacyMasterSecret;

    public CryptoService(CryptoProperties props) {
        Map<Integer, byte[]> keys = new HashMap<>();
        props.getKeys().forEach((version, base64) -> {
            if (base64 == null || base64.isBlank()) {
                return; // phiên bản không có khóa thì bỏ qua
            }

            //run openssl rand -base64 32 to GET code and set in .env

            // Khi bind @ConfigurationProperties, Spring KHÔNG báo lỗi nếu thiếu biến môi trường:
            // nó giữ nguyên chuỗi "${TEN_BIEN}". Bắt riêng trường hợp này để báo đúng nguyên nhân
            // thay vì "is not valid Base64". (Chuỗi bắt đầu bằng "${" không bao giờ là khóa thật
            // nên in ra log không lộ bí mật.)
            if (base64.trim().startsWith("${")) {
                throw new IllegalStateException("app.crypto.keys." + version
                        + " chưa có giá trị: thiếu biến môi trường (đang là '" + base64.trim() + "')");
            }

            byte[] key;
            try {
                key = Base64.getDecoder().decode(base64.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("app.crypto.keys." + version + " is not valid Base64");
            }
            if (key.length < MIN_MASTER_KEY_BYTES) {
                throw new IllegalStateException("app.crypto.keys." + version
                        + " must be at least " + MIN_MASTER_KEY_BYTES + " bytes (generate with: openssl rand -base64 32)");
            }
            keys.put(version, key);
        });

        // Fail-fast: không có khóa dùng để mã hóa thì không cho ứng dụng khởi động.
        if (!keys.containsKey(props.getActiveVersion())) {
            throw new IllegalStateException("app.crypto.active-version=" + props.getActiveVersion()
                    + " has no matching key in app.crypto.keys");
        }
        this.activeVersion = props.getActiveVersion();
        this.masterKeys = Map.copyOf(keys);
        this.legacyMasterSecret = props.getLegacyMasterSecret();
    }

    // ====================================================================== kiểu dữ liệu

    /** Kết quả mã hóa, tách sẵn theo các cột của ntt_totp_credentials. */
    public record Sealed(byte[] salt, byte[] iv, byte[] cipher, int keyVersion) {

        /** Chuỗi Base64 dạng salt|iv|cipher, dùng để giữ tạm trong Redis (EnrollSession). */
        public String toBase64() {
            ByteBuffer buf = ByteBuffer.allocate(salt.length + iv.length + cipher.length);
            buf.put(salt).put(iv).put(cipher);
            return Base64.getEncoder().encodeToString(buf.array());
        }

        public static Sealed fromBase64(String base64, int keyVersion) {
            byte[] blob;
            try {
                blob = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new CryptoException("Sealed data is not valid Base64", e);
            }
            if (blob.length <= SALT_LENGTH + IV_LENGTH + TAG_LENGTH_BYTE) {
                throw new CryptoException("Sealed data is too short: " + blob.length + " bytes", null);
            }
            return new Sealed(
                    Arrays.copyOfRange(blob, 0, SALT_LENGTH),
                    Arrays.copyOfRange(blob, SALT_LENGTH, SALT_LENGTH + IV_LENGTH),
                    Arrays.copyOfRange(blob, SALT_LENGTH + IV_LENGTH, blob.length),
                    keyVersion);
        }
    }

    /** Lỗi mã hóa / giải mã nói chung (cấu hình sai, thiếu khóa...). */
    public static class CryptoException extends RuntimeException {
        public CryptoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Tag GCM không khớp: dữ liệu bị sửa, sai adminId (AAD) hoặc sai khóa. Nên ghi audit "nghi giả mạo". */
    public static class CryptoTamperedException extends CryptoException {
        public CryptoTamperedException(Throwable cause) {
            super("Ciphertext authentication failed (tampered, wrong admin or wrong key)", cause);
        }
    }

    // ====================================================================== sinh khóa TOTP

    /** Sinh secret TOTP ngẫu nhiên dạng Base32 (RFC 4648, không padding). */
    public String generateSecretKey() {
        byte[] random = new byte[TOTP_SECRET_BYTES];
        RNG.nextBytes(random);
        try {
            return encodeBase32(random);
        } finally {
            Arrays.fill(random, (byte) 0);
        }
    }

    // ====================================================================== mã hóa / giải mã

    /** Mã hóa bằng khóa của phiên bản đang dùng (activeVersion). */
    public Sealed encrypt(String plainText, long adminId) {
        byte[] salt = randomBytes(SALT_LENGTH);
        byte[] iv = randomBytes(IV_LENGTH);
        byte[] plain = plainText.getBytes(UTF_8);
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(activeVersion, salt),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            cipher.updateAAD(aad(adminId, activeVersion));
            return new Sealed(salt, iv, cipher.doFinal(plain), activeVersion);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Encryption failed", e);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    public String decrypt(Sealed sealed, long adminId) {
        return decrypt(sealed.salt(), sealed.iv(), sealed.cipher(), sealed.keyVersion(), adminId);
    }

    /** Dùng trực tiếp với các cột của TotpCredential: kdfSalt, iv, secretCipher, keyVersion. */
    public String decrypt(byte[] salt, byte[] iv, byte[] cipherText, int keyVersion, long adminId) {
        if (salt == null || iv == null || cipherText == null
                || salt.length != SALT_LENGTH || iv.length != IV_LENGTH) {
            throw new CryptoException("Malformed encrypted data", null);
        }
        byte[] plain = null;
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(keyVersion, salt),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            cipher.updateAAD(aad(adminId, keyVersion));
            plain = cipher.doFinal(cipherText);
            return new String(plain, UTF_8);
        } catch (AEADBadTagException e) {
            throw new CryptoTamperedException(e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Decryption failed", e);
        } finally {
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    // ====================================================================== dẫn xuất khóa và AAD

    /** HKDF-SHA256 (RFC 5869) lấy ra đúng 32 byte nên expand chỉ cần 1 khối. */
    private SecretKeySpec deriveKey(int version, byte[] salt) throws GeneralSecurityException {
        byte[] ikm = masterKeys.get(version);
        if (ikm == null) {
            throw new CryptoException("Unknown key version: " + version, null);
        }
        Mac mac = Mac.getInstance(HMAC);

        // Extract: PRK = HMAC(salt, IKM)
        mac.init(new SecretKeySpec(salt, HMAC));
        byte[] prk = mac.doFinal(ikm);

        // Expand: T(1) = HMAC(PRK, info | 0x01)
        mac.init(new SecretKeySpec(prk, HMAC));
        mac.update(("totp-secret-aes-key:v" + version).getBytes(UTF_8));
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();

        Arrays.fill(prk, (byte) 0);
        try {
            return new SecretKeySpec(okm, "AES");
        } finally {
            Arrays.fill(okm, (byte) 0); // SecretKeySpec đã sao chép mảng
        }
    }

    private static byte[] aad(long adminId, int version) {
        return ("totp:" + adminId + ":v" + version).getBytes(UTF_8);
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RNG.nextBytes(bytes);
        return bytes;
    }

    // ====================================================================== Base32

    private static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32_CHARS.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
            buffer &= (1 << bits) - 1; // giữ lại phần bit chưa dùng, tránh tràn int
        }
        if (bits > 0) {
            out.append(BASE32_CHARS.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    // ====================================================================== di chuyển dữ liệu cũ

    /**
     * CHỈ dùng cho bước di chuyển một lần: giải mã bản ghi tạo bằng EncryptionUtil cũ
     * (PBKDF2, MASTER_SECRET cũ, không có AAD). Xóa hàm này sau khi di chuyển xong.
     */
    @Deprecated(forRemoval = true)
    public String decryptLegacy(byte[] salt, byte[] iv, byte[] cipherText) {
        if (legacyMasterSecret == null || legacyMasterSecret.isBlank()) {
            throw new CryptoException("app.crypto.legacy-master-secret is not configured", null);
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] keyBytes = factory.generateSecret(
                    new PBEKeySpec(legacyMasterSecret.toCharArray(), salt, 65536, 256)).getEncoded();
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return new String(cipher.doFinal(cipherText), UTF_8);
        } catch (AEADBadTagException e) {
            throw new CryptoTamperedException(e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("Legacy decryption failed", e);
        }
    }
}