package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.DependencyNodeDto;
import com.modernmvn.backend.service.MavenResolutionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maven")
@CrossOrigin(origins = "http://localhost:3000")
public class MavenController {

    private final MavenResolutionService resolutionService;

    public MavenController(MavenResolutionService resolutionService) {
        this.resolutionService = resolutionService;
    }

    @GetMapping("/resolve")
    public DependencyNodeDto resolve(@RequestParam String coordinate) {
        // coordinate format: groupId:artifactId:version
        return resolutionService.resolve(coordinate);
    }
}
