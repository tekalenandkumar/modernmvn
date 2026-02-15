package com.modernmvn.backend.controller;

import com.modernmvn.backend.dto.DependencyNode;
import com.modernmvn.backend.service.MavenResolutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maven")
public class MavenController {

    private final MavenResolutionService mavenResolutionService;

    public MavenController(MavenResolutionService mavenResolutionService) {
        this.mavenResolutionService = mavenResolutionService;
    }

    @GetMapping("/resolve")
    public DependencyNode resolve(@RequestParam String groupId, @RequestParam String artifactId,
            @RequestParam String version) {
        return mavenResolutionService.resolveDependency(groupId, artifactId, version);
    }

    @org.springframework.web.bind.annotation.PostMapping("/resolve/pom")
    public DependencyNode resolvePom(@org.springframework.web.bind.annotation.RequestBody String pomContent) {
        return mavenResolutionService.resolveFromPom(pomContent);
    }
}
