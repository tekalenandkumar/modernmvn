package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernmvn.backend.dto.ArtifactDetail;
import com.modernmvn.backend.dto.ArtifactInfo;
import com.modernmvn.backend.dto.ArtifactInfo.LicenseInfo;
import com.modernmvn.backend.dto.ArtifactVersion;
import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.dto.SearchResult;
import com.modernmvn.backend.dto.SearchResult.SearchResultItem;

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fetches artifact metadata from Maven Central Search API and the
 * Maven Central repository directly for POM-level details.
 */
@Service
public class MavenCentralService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final MavenResolutionService resolutionService;

    private static final String SEARCH_API = "https://search.maven.org/solrsearch/select";
    private static final String REPO_BASE = "https://repo1.maven.org/maven2";

    // Pre-release version pattern
    private static final Pattern PRE_RELEASE_PATTERN = Pattern.compile(
            "(?i).*(alpha|beta|rc|cr|m\\d|snapshot|preview|dev|incubating|ea).*");

    public MavenCentralService(MavenResolutionService resolutionService) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.objectMapper = new ObjectMapper();
        this.resolutionService = resolutionService;
    }

    // ──────────────────────── Public API ────────────────────────

    /**
     * Fetch all artifact metadata (versions list, recommended, license, etc.)
     */
    @Cacheable(value = "artifactInfo", key = "#groupId + ':' + #artifactId")
    public ArtifactInfo getArtifactInfo(String groupId, String artifactId) {
        try {
            // 1. Fetch the artifact summary from Solr
            JsonNode summaryDoc = fetchSolrDoc(groupId, artifactId);

            // 2. Fetch all versions via GAV core
            List<ArtifactVersion> versions = fetchAllVersions(groupId, artifactId);

            // 3. Determine recommended version
            String recommended = determineRecommendedVersion(versions);

            // 4. Try to fetch POM for description/license
            String latestVersion = summaryDoc.has("latestVersion")
                    ? summaryDoc.get("latestVersion").asText()
                    : (recommended != null ? recommended : versions.isEmpty() ? "0" : versions.get(0).version());

            PomMetadata pomMeta = fetchPomMetadata(groupId, artifactId, latestVersion);

            // Mark the recommended version
            versions = versions.stream()
                    .map(v -> new ArtifactVersion(
                            v.version(), v.packaging(), v.timestamp(), v.repository(),
                            v.isRelease(), v.version().equals(recommended)))
                    .collect(Collectors.toList());

            return new ArtifactInfo(
                    groupId,
                    artifactId,
                    latestVersion,
                    recommended,
                    summaryDoc.has("p") ? summaryDoc.get("p").asText() : "jar",
                    versions.size(),
                    versions,
                    pomMeta.description(),
                    pomMeta.url(),
                    pomMeta.licenses(),
                    summaryDoc.has("timestamp") ? summaryDoc.get("timestamp").asLong() : 0);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch artifact info for "
                    + groupId + ":" + artifactId + " — " + e.getMessage(), e);
        }
    }

    /**
     * Fetch detailed metadata for a specific version of an artifact.
     */
    @Cacheable(value = "artifactDetail", key = "#groupId + ':' + #artifactId + ':' + #version")
    public ArtifactDetail getArtifactDetail(String groupId, String artifactId, String version) {
        try {
            PomMetadata pomMeta = fetchPomMetadata(groupId, artifactId, version);

            // Generate dependency snippets
            Map<String, String> snippets = generateDependencySnippets(groupId, artifactId, version);

            // Resolve dependency count
            int depCount = 0;
            try {
                DependencyNode tree = resolutionService.resolveDependency(groupId, artifactId, version);
                depCount = countDependencies(tree);
            } catch (Exception ignored) {
                // Resolution may fail for some artifacts
            }

            // Get version timestamp from Solr
            long timestamp = fetchVersionTimestamp(groupId, artifactId, version);

            return new ArtifactDetail(
                    groupId, artifactId, version,
                    pomMeta.packaging() != null ? pomMeta.packaging() : "jar",
                    pomMeta.description(),
                    pomMeta.url(),
                    pomMeta.name(),
                    pomMeta.licenses(),
                    snippets,
                    depCount,
                    timestamp);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch artifact detail for "
                    + groupId + ":" + artifactId + ":" + version + " — " + e.getMessage(), e);
        }
    }

    // ──────────────────────── Search API ─────────────────────────

    /**
     * Search artifacts on Maven Central using free-text or structured query.
     * Supports pagination via page/pageSize.
     */
    @Cacheable(value = "searchResults", key = "#query + ':' + #page + ':' + #pageSize")
    public SearchResult searchArtifacts(String query, int page, int pageSize) {
        try {
            int start = page * pageSize;
            // Maven Central Solr supports free-text search on g, a, and tags
            String solrQuery = buildSearchQuery(query);
            String url = SEARCH_API + "?q=" + solrQuery
                    + "&rows=" + pageSize
                    + "&start=" + start
                    + "&wt=json";

            String body = httpGet(url);
            JsonNode root = objectMapper.readTree(body);
            JsonNode response = root.path("response");
            int totalResults = response.path("numFound").asInt(0);
            JsonNode docs = response.path("docs");

            List<SearchResultItem> items = new ArrayList<>();
            for (JsonNode doc : docs) {
                items.add(new SearchResultItem(
                        doc.path("g").asText(""),
                        doc.path("a").asText(""),
                        doc.path("latestVersion").asText(""),
                        doc.path("p").asText("jar"),
                        null, // description not available from Solr search
                        doc.path("timestamp").asLong(0),
                        doc.path("versionCount").asLong(0)));
            }

            return new SearchResult(query, totalResults, page, pageSize, items);
        } catch (Exception e) {
            throw new RuntimeException("Search failed for query: " + query + " — " + e.getMessage(), e);
        }
    }

    /**
     * Get recently updated artifacts from Maven Central (sorted by timestamp desc).
     */
    @Cacheable(value = "recentArtifacts", key = "#count")
    public List<SearchResultItem> getRecentlyUpdated(int count) {
        try {
            String url = SEARCH_API + "?q=*:*"
                    + "&rows=" + count
                    + "&sort=timestamp+desc"
                    + "&wt=json";

            String body = httpGet(url);
            JsonNode root = objectMapper.readTree(body);
            JsonNode docs = root.path("response").path("docs");

            List<SearchResultItem> items = new ArrayList<>();
            for (JsonNode doc : docs) {
                items.add(new SearchResultItem(
                        doc.path("g").asText(""),
                        doc.path("a").asText(""),
                        doc.path("latestVersion").asText(""),
                        doc.path("p").asText("jar"),
                        null,
                        doc.path("timestamp").asLong(0),
                        doc.path("versionCount").asLong(0)));
            }
            return items;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch recent artifacts: " + e.getMessage(), e);
        }
    }

    /**
     * Get trending/popular artifacts – curated list of well-known Java libraries
     * enriched with live metadata from Maven Central. Cached for 12 hours.
     */
    @Cacheable(value = "trendingArtifacts")
    public List<SearchResultItem> getTrendingArtifacts() {
        // Curated list of popular Java artifacts (ordered by ecosystem importance)
        String[][] trending = {
                { "org.springframework.boot", "spring-boot-starter-web" },
                { "org.springframework.boot", "spring-boot-starter-data-jpa" },
                { "com.google.guava", "guava" },
                { "org.apache.commons", "commons-lang3" },
                { "com.fasterxml.jackson.core", "jackson-databind" },
                { "org.projectlombok", "lombok" },
                { "org.slf4j", "slf4j-api" },
                { "ch.qos.logback", "logback-classic" },
                { "org.mockito", "mockito-core" },
                { "com.google.code.gson", "gson" },
                { "io.netty", "netty-all" },
                { "org.apache.kafka", "kafka-clients" },
                { "com.zaxxer", "HikariCP" },
                { "org.postgresql", "postgresql" },
                { "org.hibernate.orm", "hibernate-core" },
                { "io.micrometer", "micrometer-core" },
                { "com.squareup.okhttp3", "okhttp" },
                { "org.apache.httpcomponents.client5", "httpclient5" },
                { "io.projectreactor", "reactor-core" },
                { "org.junit.jupiter", "junit-jupiter" },
        };

        List<SearchResultItem> items = new ArrayList<>();
        for (String[] artifact : trending) {
            try {
                JsonNode doc = fetchSolrDoc(artifact[0], artifact[1]);
                items.add(new SearchResultItem(
                        artifact[0], artifact[1],
                        doc.path("latestVersion").asText(""),
                        doc.path("p").asText("jar"),
                        null,
                        doc.path("timestamp").asLong(0),
                        doc.path("versionCount").asLong(0)));
            } catch (Exception ignored) {
                // Skip artifacts that fail to resolve
            }
        }
        return items;
    }

    /**
     * Build a Solr query from user input. Uses pre-encoded %22 for quotes
     * (same pattern as fetchSolrDoc / fetchAllVersions). Supports:
     * - "groupId:artifactId" notation
     * - free text search
     */
    private String buildSearchQuery(String input) {
        if (input == null || input.isBlank())
            return "*:*";
        String trimmed = input.trim();

        // If input contains a colon, treat as g:a search
        if (trimmed.contains(":")) {
            String[] parts = trimmed.split(":", 2);
            return "g:%22" + encode(parts[0].trim()) + "%22+AND+a:%22" + encode(parts[1].trim()) + "%22";
        }

        // If input looks like a groupId (has dots but no spaces), search by group
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "g:%22" + encode(trimmed) + "%22+OR+a:%22" + encode(trimmed) + "%22";
        }

        // Free text: search across group and artifact
        return "a:%22" + encode(trimmed) + "%22+OR+g:%22" + encode(trimmed) + "%22";
    }

    // ──────────────────────── Solr queries ───────────────────────

    private JsonNode fetchSolrDoc(String groupId, String artifactId) throws Exception {
        String url = SEARCH_API + "?q=g:%22" + encode(groupId) + "%22+AND+a:%22"
                + encode(artifactId) + "%22&rows=1&wt=json";

        String body = httpGet(url);
        JsonNode root = objectMapper.readTree(body);
        JsonNode docs = root.path("response").path("docs");
        if (docs.isEmpty()) {
            throw new IllegalArgumentException("Artifact not found: " + groupId + ":" + artifactId);
        }
        return docs.get(0);
    }

    private List<ArtifactVersion> fetchAllVersions(String groupId, String artifactId) throws Exception {
        String url = SEARCH_API + "?q=g:%22" + encode(groupId) + "%22+AND+a:%22"
                + encode(artifactId) + "%22&core=gav&rows=200&wt=json&sort=timestamp+desc";

        String body = httpGet(url);
        JsonNode root = objectMapper.readTree(body);
        JsonNode docs = root.path("response").path("docs");

        List<ArtifactVersion> versions = new ArrayList<>();
        for (JsonNode doc : docs) {
            String ver = doc.get("v").asText();
            boolean isRelease = !PRE_RELEASE_PATTERN.matcher(ver).matches();
            versions.add(new ArtifactVersion(
                    ver,
                    doc.has("p") ? doc.get("p").asText() : "jar",
                    doc.has("timestamp") ? doc.get("timestamp").asLong() : 0,
                    "central",
                    isRelease,
                    false));
        }
        return versions;
    }

    private long fetchVersionTimestamp(String groupId, String artifactId, String version) {
        try {
            String url = SEARCH_API + "?q=g:%22" + encode(groupId) + "%22+AND+a:%22"
                    + encode(artifactId) + "%22+AND+v:%22" + encode(version)
                    + "%22&rows=1&wt=json";
            String body = httpGet(url);
            JsonNode root = objectMapper.readTree(body);
            JsonNode docs = root.path("response").path("docs");
            if (!docs.isEmpty() && docs.get(0).has("timestamp")) {
                return docs.get(0).get("timestamp").asLong();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    // ──────────────────── POM metadata fetch ─────────────────────

    private record PomMetadata(String description, String url, String name,
            String packaging, List<LicenseInfo> licenses) {
    }

    private PomMetadata fetchPomMetadata(String groupId, String artifactId, String version) {
        try {
            String pomUrl = buildPomUrl(groupId, artifactId, version);
            String pomXml = httpGet(pomUrl);

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new StringReader(pomXml));

            String description = model.getDescription();
            String url = model.getUrl();
            String name = model.getName();
            String packaging = model.getPackaging();

            List<LicenseInfo> licenses = new ArrayList<>();
            if (model.getLicenses() != null) {
                for (License lic : model.getLicenses()) {
                    licenses.add(new LicenseInfo(
                            lic.getName() != null ? lic.getName() : "Unknown",
                            lic.getUrl() != null ? lic.getUrl() : ""));
                }
            }

            return new PomMetadata(description, url, name, packaging, licenses);
        } catch (Exception e) {
            return new PomMetadata(null, null, null, null, List.of());
        }
    }

    private String buildPomUrl(String groupId, String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        return REPO_BASE + "/" + groupPath + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".pom";
    }

    // ────────────────── Recommended version logic ────────────────

    /**
     * Determines the recommended version: latest stable release
     * (non-SNAPSHOT, non-alpha, non-beta, non-RC, non-milestone).
     */
    private String determineRecommendedVersion(List<ArtifactVersion> versions) {
        // Versions are sorted desc by timestamp
        return versions.stream()
                .filter(ArtifactVersion::isRelease)
                .map(ArtifactVersion::version)
                .findFirst()
                .orElse(versions.isEmpty() ? null : versions.get(0).version());
    }

    // ────────────────── Dependency snippets ──────────────────────

    private Map<String, String> generateDependencySnippets(String groupId, String artifactId, String version) {
        Map<String, String> snippets = new LinkedHashMap<>();

        // Maven
        snippets.put("maven", String.format(
                "<dependency>\n    <groupId>%s</groupId>\n    <artifactId>%s</artifactId>\n    <version>%s</version>\n</dependency>",
                groupId, artifactId, version));

        // Gradle (Groovy)
        snippets.put("gradle", String.format(
                "implementation '%s:%s:%s'",
                groupId, artifactId, version));

        // Gradle (Kotlin DSL)
        snippets.put("gradle_kotlin", String.format(
                "implementation(\"%s:%s:%s\")",
                groupId, artifactId, version));

        // SBT
        snippets.put("sbt", String.format(
                "libraryDependencies += \"%s\" %% \"%s\" %% \"%s\"",
                groupId, artifactId, version));

        // Ivy
        snippets.put("ivy", String.format(
                "<dependency org=\"%s\" name=\"%s\" rev=\"%s\" />",
                groupId, artifactId, version));

        // Leiningen
        snippets.put("leiningen", String.format(
                "[%s/%s \"%s\"]",
                groupId, artifactId, version));

        // Apache Buildr
        snippets.put("buildr", String.format(
                "'%s:%s:jar:%s'",
                groupId, artifactId, version));

        return snippets;
    }

    // ────────────────────── Helpers ──────────────────────────────

    private int countDependencies(DependencyNode node) {
        if (node == null || node.children() == null)
            return 0;
        int count = node.children().size();
        for (DependencyNode child : node.children()) {
            count += countDependencies(child);
        }
        return count;
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "modernmvn/1.0")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + " from " + url);
        }
        return response.body();
    }

    private String encode(String value) {
        return value.replace(" ", "%20");
    }
}
