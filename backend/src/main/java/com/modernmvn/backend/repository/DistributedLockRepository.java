package com.modernmvn.backend.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * Utility for acquiring and releasing PostgreSQL advisory locks.
 * These are transaction-aware, distributed locks that prevent race conditions
 * when multiple application instances attempt to index the same artifact
 * simultaneously.
 */
@Repository
public class DistributedLockRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Attempts to acquire a non-blocking advisory lock.
     * 
     * @param key The lock key (e.g. "groupId:artifactId:version")
     * @return true if the lock was acquired, false if it is already held by another
     *         session.
     */
    public boolean tryLock(String key) {
        // Use a 64-bit hash instead of 32-bit String.hashCode() to reduce collision
        // risk
        long hash = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .getMostSignificantBits();
        return (Boolean) em.createNativeQuery("SELECT pg_try_advisory_lock(:key)")
                .setParameter("key", hash)
                .getSingleResult();
    }

    /**
     * Releases an advisory lock.
     * 
     * @param key The lock key
     */
    public void unlock(String key) {
        long hash = java.util.UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .getMostSignificantBits();
        em.createNativeQuery("SELECT pg_advisory_unlock(:key)")
                .setParameter("key", hash)
                .getSingleResult();
    }
}
