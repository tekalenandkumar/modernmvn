package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.ArtifactDetail;
import com.modernmvn.backend.dto.ArtifactInfo;
import com.modernmvn.backend.dto.SearchResult;
import com.modernmvn.backend.dto.SearchResult.SearchResultItem;
import com.modernmvn.backend.service.MavenCentralService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maven")
public class ArtifactController {

    private final MavenCentralService mavenCentralService;

    public ArtifactController(MavenCentralService mavenCentralService) {
        this.mavenCentralService = mavenCentralService;
    }

    // ─────────────────────── Search ───────────────────────────

    /**
     * GET /api/maven/search?q=...&page=0&size=20
     * Full-text artifact search with pagination.
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchArtifacts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            if (q.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Query parameter 'q' is required"));
            }
            size = Math.min(size, 50); // cap at 50
            SearchResult result = mavenCentralService.searchArtifacts(q, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Search failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/maven/recent?count=20
     * Returns recently updated artifacts.
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentlyUpdated(
            @RequestParam(defaultValue = "20") int count) {
        try {
            count = Math.min(count, 50);
            List<SearchResultItem> items = mavenCentralService.getRecentlyUpdated(count);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch recent artifacts: " + e.getMessage()));
        }
    }

    /**
     * GET /api/maven/trending
     * Returns curated list of popular/trending artifacts.
     */
    @GetMapping("/trending")
    public ResponseEntity<?> getTrendingArtifacts() {
        try {
            List<SearchResultItem> items = mavenCentralService.getTrendingArtifacts();
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch trending artifacts: " + e.getMessage()));
        }
    }

    // ─────────────── Group Browse ─────────────────────────────

    /**
     * GET /api/maven/group/{groupId}?page=0&size=20
     * Returns paginated list of all artifacts under a given groupId.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> getGroupArtifacts(
            @PathVariable String groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            size = Math.min(size, 50);
            SearchResult result = mavenCentralService.searchByGroup(groupId, page, size);
            if (result.totalResults() == 0) {
                return ResponseEntity.status(404)
                        .body(Map.of("error", "No artifacts found for group: " + groupId));
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to browse group: " + e.getMessage()));
        }
    }

    // ─────────────── Reverse Dependencies ────────────────────

    /**
     * GET /api/maven/artifact/{groupId}/{artifactId}/usedby?page=0&size=10
     * Returns paginated list of artifacts that depend on this artifact.
     */
    @GetMapping("/artifact/{groupId}/{artifactId}/usedby")
    public ResponseEntity<?> getReverseDependencies(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            size = Math.min(size, 50);
            SearchResult result = mavenCentralService.getReverseDependencies(groupId, artifactId, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch reverse dependencies: " + e.getMessage()));
        }
    }

    /**
     * GET /api/maven/artifact/{groupId}/{artifactId}/usedby/count
     * Returns just the count of artifacts that depend on this artifact.
     */
    @GetMapping("/artifact/{groupId}/{artifactId}/usedby/count")
    public ResponseEntity<?> getReverseDependencyCount(
            @PathVariable String groupId,
            @PathVariable String artifactId) {
        try {
            int count = mavenCentralService.getReverseDependencyCount(groupId, artifactId);
            return ResponseEntity.ok(Map.of("count", count));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch reverse dependency count: " + e.getMessage()));
        }
    }

    // ─────────────────── Artifact Detail ─────────────────────

    /**
     * GET /api/maven/artifact/{groupId}/{artifactId}
     * Returns artifact summary with all versions.
     */
    @GetMapping("/artifact/{groupId}/{artifactId}")
    public ResponseEntity<?> getArtifactInfo(
            @PathVariable String groupId,
            @PathVariable String artifactId) {
        try {
            ArtifactInfo info = mavenCentralService.getArtifactInfo(groupId, artifactId);
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Failed to fetch artifact info: " + e.getMessage()));
        }
    }

    /**
     * GET /api/maven/artifact/{groupId}/{artifactId}/{version}
     * Returns detailed info for a specific version.
     */
    @GetMapping("/artifact/{groupId}/{artifactId}/{version}")
    public ResponseEntity<?> getArtifactDetail(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @PathVariable String version) {
        try {
            ArtifactDetail detail = mavenCentralService.getArtifactDetail(groupId, artifactId, version);
            return ResponseEntity.ok(detail);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Failed to fetch artifact details: " + e.getMessage()));
        }
    }
}
