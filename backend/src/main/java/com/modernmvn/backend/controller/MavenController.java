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
    private final com.modernmvn.backend.service.PomParserService pomParserService;

    public MavenController(MavenResolutionService mavenResolutionService,
            com.modernmvn.backend.service.PomParserService pomParserService) {
        this.mavenResolutionService = mavenResolutionService;
        this.pomParserService = pomParserService;
    }

    @GetMapping("/resolve")
    public DependencyNode resolve(@RequestParam String groupId, @RequestParam String artifactId,
            @RequestParam String version) {
        return mavenResolutionService.resolveDependency(groupId, artifactId, version);
    }

    @org.springframework.web.bind.annotation.PostMapping("/resolve/pom")
    public DependencyNode resolvePom(@org.springframework.web.bind.annotation.RequestBody String pomContent) {
        com.modernmvn.backend.dto.MavenCoordinates coordinates = pomParserService.parsePom(pomContent);
        return mavenResolutionService.resolveDependency(coordinates.groupId(), coordinates.artifactId(),
                coordinates.version());
    }
}
