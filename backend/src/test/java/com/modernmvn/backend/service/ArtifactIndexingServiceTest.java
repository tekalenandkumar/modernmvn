package com.modernmvn.backend.service;

import com.modernmvn.backend.entity.SecuritySummaryEntity;
import com.modernmvn.backend.repository.SecuritySummaryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactIndexingServiceTest {

    @InjectMocks
    private ArtifactIndexingService indexingService;

    @Mock
    private SecuritySummaryRepository summaryRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetSecurityHistoryWithCutoff() {
        String groupId = "org.test";
        String artifactId = "test-artifact";
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        when(summaryRepository.findHistory(eq(groupId), eq(artifactId), any(Instant.class)))
                .thenReturn(List.of(new SecuritySummaryEntity()));

        List<SecuritySummaryEntity> history = indexingService.getSecurityHistory(groupId, artifactId, cutoff);

        assertNotNull(history);
        verify(summaryRepository).findHistory(groupId, artifactId, cutoff);
    }

    @Test
    void testGetSecurityHistoryWithNullCutoff() {
        String groupId = "org.test";
        String artifactId = "test-artifact";

        when(summaryRepository.findHistory(eq(groupId), eq(artifactId), any(Instant.class)))
                .thenReturn(List.of(new SecuritySummaryEntity()));

        List<SecuritySummaryEntity> history = indexingService.getSecurityHistory(groupId, artifactId, null);

        assertNotNull(history);
        // Verify it defaults to 365 days ago (roughly)
        verify(summaryRepository).findHistory(eq(groupId), eq(artifactId),
                argThat(instant -> instant.isBefore(Instant.now().minus(360, ChronoUnit.DAYS))));
    }
}
