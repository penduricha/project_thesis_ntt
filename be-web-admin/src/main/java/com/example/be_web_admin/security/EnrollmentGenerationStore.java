package com.example.be_web_admin.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Generation-scoped Redis state. A claimed state is retained on ambiguous DB outcomes. */
@Component
public class EnrollmentGenerationStore {
    public record Pending(String generation, String state, String secretCipher, int keyVersion, int periodSeconds) {}
    public record Claim(String token, Pending pending) {}
    /** Only server-side code may see the claim token; never return it to a client. */
    public record ClaimedGeneration(String generation, String token) {}
    public static class BusyException extends RuntimeException {}

    private static <T> DefaultRedisScript<T> script(String name, Class<T> type) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/" + name + ".lua"));
        script.setResultType(type);
        return script;
    }
    private static final DefaultRedisScript<Long> START = script("enrollment-start", Long.class);
    private static final DefaultRedisScript<String> CLAIM = script("enrollment-claim", String.class);
    private static final DefaultRedisScript<Long> CANCEL = script("enrollment-cancel", Long.class);
    private static final DefaultRedisScript<Long> FINISH = script("enrollment-finish", Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    public EnrollmentGenerationStore(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }
    public static boolean validGeneration(String value) {
        return value != null && value.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    }
    private String key(Long adminId) { return "totp:enrollment:v2:{" + adminId + "}"; }
    private String json(Pending pending) {
        try { return mapper.writeValueAsString(pending); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize enrollment", e); }
    }
    public String start(Long adminId, String cipher, int version, int period, int ttl) {
        String generation = UUID.randomUUID().toString();
        Long result = redis.execute(START, List.of(key(adminId)), json(new Pending(generation, "READY", cipher, version, period)), Integer.toString(ttl));
        if (result == null || result < 0) throw new IllegalStateException("Invalid enrollment state");
        if (result == 0) throw new BusyException();
        return generation;
    }
    public Claim claim(Long adminId, String generation, Integer period) {
        String token = UUID.randomUUID().toString();
        String raw = redis.execute(CLAIM, List.of(key(adminId)), generation, token, period == null ? "" : period.toString());
        if (raw == null) return null;
        try { return new Claim(token, mapper.readValue(raw, Pending.class)); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid enrollment state", e); }
    }
    public boolean cancel(Long adminId, String generation) {
        Long result = redis.execute(CANCEL, List.of(key(adminId)), generation);
        if (result == null) throw new IllegalStateException("Enrollment cancel unavailable");
        if (result < 0) throw new BusyException();
        return result == 1;
    }
    /** Snapshot for reconciliation. Finishing still compares both generation and owner atomically. */
    public ClaimedGeneration claimed(Long adminId, String generation) {
        if (!validGeneration(generation)) throw new IllegalArgumentException("Invalid generation");
        String raw = redis.opsForValue().get(key(adminId));
        if (raw == null) return null;
        try {
            var node = mapper.readTree(raw);
            if (node == null || !node.isObject() || !node.path("generation").isTextual() ||
                    !node.path("state").isTextual()) throw new IllegalStateException("Invalid enrollment state");
            if (!generation.equals(node.path("generation").textValue())) return null;
            String state = node.path("state").textValue();
            if ("READY".equals(state)) return null;
            if (!"CLAIMED".equals(state) || !node.path("claim").isTextual() ||
                    !validGeneration(node.path("claim").textValue())) {
                throw new IllegalStateException("Invalid enrollment claim");
            }
            return new ClaimedGeneration(generation, node.path("claim").textValue());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid enrollment state", e);
        }
    }

    public boolean finishIfOwned(Long adminId, String generation, String claim) {
        Long result = redis.execute(FINISH, List.of(key(adminId)), generation, claim);
        if (result == null || (result != 0 && result != 1)) throw new IllegalStateException("Enrollment finish unavailable");
        return result == 1;
    }
    public void finish(Long adminId, String generation, String claim) {
        if (!finishIfOwned(adminId, generation, claim)) throw new IllegalStateException("Enrollment claim changed");
    }
}
