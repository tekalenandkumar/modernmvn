import { MetadataRoute } from 'next';

/**
 * Dynamic sitemap for modernmvn.com.
 * 
 * Currently includes static pages. In production, this would be
 * expanded to include dynamically discovered artifact pages from
 * a database or search index. For now we include the core routes
 * and a few popular artifacts as examples.
 */
export default function sitemap(): MetadataRoute.Sitemap {
    const baseUrl = process.env.NEXT_PUBLIC_BASE_URL || 'https://modernmvn.com';

    // Core pages
    const staticPages: MetadataRoute.Sitemap = [
        {
            url: baseUrl,
            lastModified: new Date(),
            changeFrequency: 'daily',
            priority: 1,
        },
        {
            url: `${baseUrl}/analyze`,
            lastModified: new Date(),
            changeFrequency: 'weekly',
            priority: 0.9,
        },
        {
            url: `${baseUrl}/search`,
            lastModified: new Date(),
            changeFrequency: 'daily',
            priority: 0.9,
        },
    ];

    // Popular artifact pages (seeds for initial indexing)
    // In production, these would come from a DB query of most-viewed artifacts
    const popularArtifacts = [
        { g: 'org.springframework.boot', a: 'spring-boot-starter-web' },
        { g: 'org.springframework.boot', a: 'spring-boot-starter-data-jpa' },
        { g: 'com.google.guava', a: 'guava' },
        { g: 'org.apache.commons', a: 'commons-lang3' },
        { g: 'com.fasterxml.jackson.core', a: 'jackson-databind' },
        { g: 'org.slf4j', a: 'slf4j-api' },
        { g: 'ch.qos.logback', a: 'logback-classic' },
        { g: 'org.projectlombok', a: 'lombok' },
        { g: 'junit', a: 'junit' },
        { g: 'org.mockito', a: 'mockito-core' },
        { g: 'com.google.code.gson', a: 'gson' },
        { g: 'org.apache.httpcomponents.client5', a: 'httpclient5' },
        { g: 'io.netty', a: 'netty-all' },
        { g: 'org.apache.kafka', a: 'kafka-clients' },
        { g: 'com.zaxxer', a: 'HikariCP' },
        { g: 'org.postgresql', a: 'postgresql' },
        { g: 'mysql', a: 'mysql-connector-java' },
        { g: 'org.hibernate.orm', a: 'hibernate-core' },
        { g: 'io.micrometer', a: 'micrometer-core' },
        { g: 'org.apache.maven', a: 'maven-core' },
    ];

    const artifactPages: MetadataRoute.Sitemap = popularArtifacts.map(({ g, a }) => ({
        url: `${baseUrl}/artifact/${g}/${a}`,
        lastModified: new Date(),
        changeFrequency: 'weekly' as const,
        priority: 0.7,
    }));

    return [...staticPages, ...artifactPages];
}
