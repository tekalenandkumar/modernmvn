package com.modernmvn.backend.repository;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class DistributedLockRepositoryTest {

    @Test
    void testHashingStability() {
        String key = "org.springframework.boot:spring-boot-starter-web";

        long hash1 = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();
        long hash2 = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();

        assertEquals(hash1, hash2, "Hash must be stable for the same key");

        String key2 = "org.springframework.boot:spring-boot-starter-web-2";
        long hash3 = UUID.nameUUIDFromBytes(key2.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits();

        assertNotEquals(hash1, hash3, "Different keys must produce different hashes");
    }
}
