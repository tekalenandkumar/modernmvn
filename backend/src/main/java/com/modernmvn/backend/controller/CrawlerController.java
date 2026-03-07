package com.modernmvn.backend.controller;

import com.modernmvn.backend.entity.CrawlerStateEntity;
import com.modernmvn.backend.worker.MavenCrawlerWorker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Read-only status endpoint for the Maven Central crawler.
 * The crawler runs automatically on startup — no manual trigger needed.
 *
 * GET /api/admin/crawler/status — returns cursor, total discovered, % complete
 */
@RestController
@RequestMapping("/api/admin/crawler")
public class CrawlerController {

    private final MavenCrawlerWorker crawlerWorker;

    public CrawlerController(MavenCrawlerWorker crawlerWorker) {
        this.crawlerWorker = crawlerWorker;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        CrawlerStateEntity state = crawlerWorker.getStatus();
        return ResponseEntity.ok(Map.of(
                "cursorMark", state.getCursorMark(),
                "cursorOffset", state.getCursorOffset(),
                "totalDiscovered", state.getTotalDiscovered(),
                "totalArtifactsOnMavenCentral", state.getTotalArtifacts(),
                "lastRun", state.getLastRun() != null ? state.getLastRun().toString() : "never",
                "percentComplete", state.getTotalArtifacts() > 0
                        ? String.format("%.2f%%",
                                (state.getTotalDiscovered() / (double) state.getTotalArtifacts()) * 100)
                        : "unknown"));
    }
}
