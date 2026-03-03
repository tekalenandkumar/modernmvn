package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.dto.MultiModuleResult;
import com.modernmvn.backend.dto.PomUploadRequest;
import com.modernmvn.backend.service.MavenResolutionService;
import com.modernmvn.backend.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/maven")
@Validated
public class MavenController {

    private final MavenResolutionService mavenResolutionService;
    private final RateLimiterService rateLimiterService;

    // 512 KB file upload limit
    private static final long MAX_FILE_SIZE = 512 * 1024;

    public MavenController(MavenResolutionService mavenResolutionService, RateLimiterService rateLimiterService) {
        this.mavenResolutionService = mavenResolutionService;
        this.rateLimiterService = rateLimiterService;
    }

    private boolean isAllowed(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return rateLimiterService.tryConsume(ip + ":resolve", 10, 1);
    }

    @GetMapping("/resolve")
    public ResponseEntity<?> resolve(
            @RequestParam @Pattern(regexp = "[a-zA-Z0-9._-]+") String groupId,
            @RequestParam @Pattern(regexp = "[a-zA-Z0-9._-]+") String artifactId,
            @RequestParam @Pattern(regexp = "[a-zA-Z0-9._-]+") String version,
            @RequestParam(required = false) List<String> repos,
            HttpServletRequest request) {

        if (!isAllowed(request)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Try again in a minute."));
        }

        if (repos != null && !repos.isEmpty()) {
            return ResponseEntity
                    .ok(mavenResolutionService.resolveDependencyWithRepos(groupId, artifactId, version, repos));
        }
        return ResponseEntity.ok(mavenResolutionService.resolveDependency(groupId, artifactId, version));
    }

    /**
     * Resolve from raw POM content (text/plain body) — backward compatible.
     */
    @PostMapping("/resolve/pom")
    public ResponseEntity<?> resolvePom(@RequestBody String pomContent, HttpServletRequest request) {
        if (!isAllowed(request)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Try again in a minute."));
        }
        return ResponseEntity.ok(mavenResolutionService.resolveFromPom(pomContent));
    }

    /**
     * Enhanced POM resolution with custom repos and multi-module support.
     */
    @PostMapping("/resolve/pom/advanced")
    public ResponseEntity<?> resolvePomAdvanced(@RequestBody PomUploadRequest request, HttpServletRequest httpRequest) {
        if (!isAllowed(httpRequest)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Try again in a minute."));
        }
        try {
            if (request.detectMultiModule()) {
                MultiModuleResult result = mavenResolutionService.resolveMultiModule(
                        request.pomContent(), request.customRepositories());
                return ResponseEntity.ok(result);
            } else {
                DependencyNode result = mavenResolutionService.resolveFromPomWithRepos(
                        request.pomContent(), request.customRepositories());
                return ResponseEntity.ok(result);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Upload a POM file directly (multipart form).
     */
    @PostMapping("/resolve/upload")
    public ResponseEntity<?> resolveUpload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) List<String> repos,
            @RequestParam(required = false, defaultValue = "true") boolean detectMultiModule,
            HttpServletRequest httpRequest) {
        if (!isAllowed(httpRequest)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Rate limit exceeded. Try again in a minute."));
        }
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty."));
            }
            if (file.getSize() > MAX_FILE_SIZE) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File exceeds maximum size of " + (MAX_FILE_SIZE / 1024) + " KB."));
            }

            String filename = file.getOriginalFilename();
            if (filename != null && !filename.endsWith(".xml") && !filename.endsWith(".pom")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Only .xml and .pom files are accepted."));
            }

            String pomContent = new String(file.getBytes(), StandardCharsets.UTF_8);

            if (detectMultiModule) {
                MultiModuleResult result = mavenResolutionService.resolveMultiModule(pomContent,
                        repos != null ? repos : List.of());
                return ResponseEntity.ok(result);
            } else {
                DependencyNode result = mavenResolutionService.resolveFromPomWithRepos(pomContent,
                        repos != null ? repos : List.of());
                return ResponseEntity.ok(result);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to read uploaded file."));
        }
    }

    /**
     * Security info endpoint — returns limits and disclaimers.
     */
    @GetMapping("/limits")
    public Map<String, Object> getLimits() {
        return Map.of(
                "maxPomSizeKB", 512,
                "maxCustomRepos", 5,
                "allowedRepoSchemes", List.of("https"),
                "sessionTimeoutMinutes", 30,
                "disclaimer",
                "Uploaded POM files are processed in-memory and not stored permanently. " +
                        "Custom repository URLs must use HTTPS. Analysis results are cached for 24 hours. " +
                        "Do not upload POMs containing sensitive credentials or proprietary repository URLs.");
    }
}
