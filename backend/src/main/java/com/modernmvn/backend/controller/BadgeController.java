package com.modernmvn.backend.controller;

import com.modernmvn.backend.service.MavenCentralService;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * Generates embeddable SVG version badges for Maven artifacts.
 * Similar to shields.io but hosted on modernmvn for brand visibility.
 *
 * Example: GET /badge/org.springframework.boot/spring-boot-starter-web
 * Returns an SVG badge showing "maven | v3.5.3"
 */
@RestController
@RequestMapping("/badge")
public class BadgeController {

    private final MavenCentralService mavenCentralService;

    public BadgeController(MavenCentralService mavenCentralService) {
        this.mavenCentralService = mavenCentralService;
    }

    /**
     * GET /badge/{groupId}/{artifactId}
     * Returns an SVG badge showing the latest version.
     *
     * @param style "flat" or "default" (rounded)
     */
    @GetMapping(value = "/{groupId}/{artifactId}", produces = "image/svg+xml")
    public ResponseEntity<String> getBadge(
            @PathVariable String groupId,
            @PathVariable String artifactId,
            @RequestParam(defaultValue = "default") String style) {

        String version = mavenCentralService.getLatestVersion(groupId, artifactId);

        if (version.isEmpty()) {
            version = "unknown";
        }

        String svg = generateBadgeSvg("maven", "v" + version, style);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header("Access-Control-Allow-Origin", "*")
                .body(svg);
    }

    /**
     * Generates a shields.io-style SVG badge inline.
     * No external dependencies needed.
     */
    private String generateBadgeSvg(String label, String value, String style) {
        // Approximate character widths (monospace-ish for SVG)
        int labelWidth = measureText(label) + 12;
        int valueWidth = measureText(value) + 12;
        int totalWidth = labelWidth + valueWidth;

        boolean isFlat = "flat".equalsIgnoreCase(style);
        String borderRadius = isFlat ? "0" : "3";

        return String.format(
                """
                        <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="20" role="img" aria-label="%s: %s">
                          <title>%s: %s</title>
                          <linearGradient id="s" x2="0" y2="100%%">
                            <stop offset="0" stop-color="#bbb" stop-opacity=".1"/>
                            <stop offset="1" stop-opacity=".1"/>
                          </linearGradient>
                          <clipPath id="r">
                            <rect width="%d" height="20" rx="%s" fill="#fff"/>
                          </clipPath>
                          <g clip-path="url(#r)">
                            <rect width="%d" height="20" fill="#555"/>
                            <rect x="%d" width="%d" height="20" fill="#4c1"/>
                            <rect width="%d" height="20" fill="url(#s)"/>
                          </g>
                          <g fill="#fff" text-anchor="middle" font-family="Verdana,Geneva,DejaVu Sans,sans-serif" text-rendering="geometricPrecision" font-size="11">
                            <text aria-hidden="true" x="%d" y="15" fill="#010101" fill-opacity=".3">%s</text>
                            <text x="%d" y="14">%s</text>
                            <text aria-hidden="true" x="%d" y="15" fill="#010101" fill-opacity=".3">%s</text>
                            <text x="%d" y="14">%s</text>
                          </g>
                        </svg>
                        """,
                totalWidth, label, value,
                label, value,
                totalWidth, borderRadius,
                labelWidth, labelWidth, valueWidth, totalWidth,
                labelWidth / 2 + 1, label,
                labelWidth / 2 + 1, label,
                labelWidth + valueWidth / 2, value,
                labelWidth + valueWidth / 2, value);
    }

    /**
     * Rough text width measurement for SVG badge layout.
     * Uses approximate character widths for Verdana 11px.
     */
    private int measureText(String text) {
        double width = 0;
        for (char c : text.toCharArray()) {
            if (Character.isUpperCase(c)) {
                width += 7.5;
            } else if (Character.isDigit(c) || c == '.') {
                width += 6.5;
            } else {
                width += 6.1;
            }
        }
        return (int) Math.ceil(width);
    }
}
