
package com.example.be_web_admin.security.private_key;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

public class EncryptionUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int ITERATION_COUNT = 65536;
    private static final int KEY_LENGTH = 256;

    private static final String MASTER_SECRET =
            "HeThongBaoMat2FA_NTT_2026_TQN";

    /**
     * Hàm sinh khóa AES từ Master Password dựa trên PBKDF2
     */
    private static SecretKey getSecretKey(char[] password, byte[] salt)
            throws Exception {

        SecretKeyFactory factory =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

        KeySpec spec = new PBEKeySpec(
                password, salt, ITERATION_COUNT, KEY_LENGTH
        );

        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    /**
     * Mã hóa Secret Key của người dùng
     */
    public static String encrypt(String plainText) {
        try {
            byte[] salt = new byte[SALT_LENGTH_BYTE];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(salt);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            secureRandom.nextBytes(iv);

            SecretKey secretKey =
                    getSecretKey(MASTER_SECRET.toCharArray(), salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText =
                    cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(
                    salt.length + iv.length + cipherText.length
            );

            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder()
                    .encodeToString(byteBuffer.array());

        } catch (Exception e) {
            throw new RuntimeException(
                    "Lỗi mã hóa dữ liệu: " + e.getMessage(), e
            );
        }
    }

    /**
     * Giải mã Secret Key khi cần đối soát TOTP
     */
    public static String decrypt(String encryptedStr) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedStr);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);

            byte[] salt = new byte[SALT_LENGTH_BYTE];
            byteBuffer.get(salt);

            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            SecretKey secretKey =
                    getSecretKey(MASTER_SECRET.toCharArray(), salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(TAG_LENGTH_BIT, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Lỗi giải mã dữ liệu: " + e.getMessage(), e
            );
        }
    }

    /**
     * Chuyển đổi byte[] sang Base32 theo RFC 4648
     * Dùng cho Secret Key của TOTP / Google Authenticator.
     */
    private static String encodeBase32(byte[] data) {

        final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        StringBuilder result = new StringBuilder();

        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {

            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;

            while (bitsLeft >= 5) {

                result.append(
                        BASE32_CHARS.charAt(
                                (buffer >> (bitsLeft - 5)) & 0x1F
                        )
                );

                bitsLeft -= 5;
            }
        }

        if (bitsLeft > 0) {
            result.append(
                    BASE32_CHARS.charAt(
                            (buffer << (5 - bitsLeft)) & 0x1F
                    )
            );
        }

        // Không thêm padding '=' để phù hợp với cách sử dụng TOTP phổ biến.
        return result.toString();
    }

    /**
     * Sinh Secret Key 2FA dưới dạng Base32
     */
    public static String generateSecretKey() {

        SecureRandom secureRandom = new SecureRandom();

        // Tạo mảng 20 bytes ngẫu nhiên an toàn (160 bits)
        byte[] randomBytes = new byte[20];
        secureRandom.nextBytes(randomBytes);

        // ==========================================
        // CODE CŨ: Chuyển đổi sang Base64URL
        // ==========================================

        // return Base64.getUrlEncoder()
        //         .withoutPadding()
        //         .encodeToString(randomBytes);

        // ==========================================
        // CODE MỚI: Chuyển đổi sang Base32 (RFC 4648)
        // ==========================================

        return encodeBase32(randomBytes);
    }

    public static void main(String[] args) {

        String secretKey = generateSecretKey();

        System.out.println("Khóa bí mật 2FA (Base32): " + secretKey);

        System.out.println("=== KIỂM TRA MÃ HÓA & GIẢI MÃ 2FA ===");

        System.out.println("1. Chuỗi gốc (Plain Text): " + secretKey);

        // 1. Mã hóa trước khi lưu xuống MariaDB
        String encryptedData = encrypt(secretKey);

        System.out.println("2. Chuỗi sau khi mã hóa (Lưu DB): "
                + encryptedData);

        // 2. Giải mã khi cần đối soát TOTP
        String decryptedData = decrypt(encryptedData);

        System.out.println("3. Chuỗi sau khi giải mã: "
                + decryptedData);

        // Kiểm tra tính toàn vẹn
        if (secretKey.equals(decryptedData)) {

            System.out.println(
                    "\n=> KẾT QUẢ: Thành công! "
                            + "Dữ liệu giải mã khớp hoàn toàn với bản gốc."
            );

        } else {

            System.out.println(
                    "\n=> KẾT QUẢ: Thất bại! Dữ liệu không khớp."
            );
        }
    }
}
