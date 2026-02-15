package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.ArtifactDetail;
import com.modernmvn.backend.dto.ArtifactInfo;
import com.modernmvn.backend.service.MavenCentralService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/maven/artifact")
public class ArtifactController {

    private final MavenCentralService mavenCentralService;

    public ArtifactController(MavenCentralService mavenCentralService) {
        this.mavenCentralService = mavenCentralService;
    }

    /**
     * GET /api/maven/artifact/{groupId}/{artifactId}
     * Returns artifact summary with all versions.
     */
    @GetMapping("/{groupId}/{artifactId}")
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
    @GetMapping("/{groupId}/{artifactId}/{version}")
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
