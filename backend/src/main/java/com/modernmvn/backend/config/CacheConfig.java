package com.modernmvn.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

        private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

        @Bean
        public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofHours(24))
                                .disableCachingNullValues()
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair
                                                                .fromSerializer(new StringRedisSerializer()))
                                .serializeValuesWith(RedisSerializationContext.SerializationPair
                                                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

                // Artifact caches with specific TTLs
                Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
                cacheConfigurations.put("artifactInfo",
                                defaultConfig.entryTtl(Duration.ofHours(6)));
                cacheConfigurations.put("artifactDetail",
                                defaultConfig.entryTtl(Duration.ofHours(24)));
                // Search & discovery caches
                cacheConfigurations.put("searchResults",
                                defaultConfig.entryTtl(Duration.ofMinutes(10)));
                cacheConfigurations.put("recentArtifacts",
                                defaultConfig.entryTtl(Duration.ofMinutes(15)));
                cacheConfigurations.put("trendingArtifacts",
                                defaultConfig.entryTtl(Duration.ofHours(12)));
                // Security & vulnerability caches
                cacheConfigurations.put("vulnerabilities",
                                defaultConfig.entryTtl(Duration.ofHours(1)));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(defaultConfig)
                                .withInitialCacheConfigurations(cacheConfigurations)
                                .build();
        }

        /**
         * Gracefully handle cache errors — log and continue without caching
         * instead of failing the request.
         */
        @Override
        public CacheErrorHandler errorHandler() {
                return new CacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Cache GET failed for cache={} key={}: {}", cache.getName(), key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                        Object value) {
                                log.warn("Cache PUT failed for cache={} key={}: {}", cache.getName(), key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Cache EVICT failed for cache={} key={}: {}", cache.getName(), key,
                                                exception.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception, Cache cache) {
                                log.warn("Cache CLEAR failed for cache={}: {}", cache.getName(),
                                                exception.getMessage());
                        }
                };
        }
}
