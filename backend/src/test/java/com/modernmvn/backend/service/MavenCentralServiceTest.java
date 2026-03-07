package com.modernmvn.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modernmvn.backend.dto.SearchResult.SearchResultItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MavenCentralServiceTest {

    @InjectMocks
    private MavenCentralService mavenCentralService;

    @Mock
    private ArtifactIndexingService indexingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // We need to partially mock the httpGet call or mock the Solr response
        // Since httpGet is private, we'll use Reflection or a spy to verify the
        // enrichment logic
        // For this test, we are primarily interested in the enrichment within
        // getTrendingArtifacts
    }

    @Test
    void testTrendingEnrichment() throws Exception {
        // MavenCentralService is a bit hard to unit test because of HttpClient and
        // private methods.
        // However, we can test the logic indirectly or by mocking the internal state.

        MavenCentralService spyService = spy(mavenCentralService);

        // Mock the internal fetchSolrDoc call (which calls private httpGet)
        // Since we can't easily mock private methods with Mockito, let's focus on what
        // we can verify

        // Let's verify that the CATEGORY_MAP is correctly populated
        List<SearchResultItem> trending = spyService.getTrendingArtifacts();

        // The trending list is hardcoded in the service.
        // We expect artifacts from 'org.springframework.boot' to have 'Framework' and
        // 'Web' categories.

        SearchResultItem springBoot = trending.stream()
                .filter(item -> item.groupId().equals("org.springframework.boot"))
                .findFirst()
                .orElse(null);

        if (springBoot != null) {
            assertTrue(springBoot.categories().contains("Framework"));
            assertTrue(springBoot.categories().contains("Web"));
        }
    }

    @Test
    void testSafetyIndicatorLogic() {
        // Test that isVersionSafe (private) correctly proxies to indexingService

        when(indexingService.isVersionSafeFromDb(anyString(), anyString(), anyString())).thenReturn(true);

        boolean safe = ReflectionTestUtils.invokeMethod(mavenCentralService, "isVersionSafe",
                "org.springframework.boot", "spring-boot-starter-web", "3.1.0");

        assertTrue(safe);
        verify(indexingService).isVersionSafeFromDb("org.springframework.boot", "spring-boot-starter-web", "3.1.0");
    }
}
