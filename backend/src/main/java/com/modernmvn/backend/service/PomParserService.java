package com.modernmvn.backend.service;

import com.modernmvn.backend.dto.MavenCoordinates;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;

@Service
public class PomParserService {

    public MavenCoordinates parsePom(String pomContent) {
        if (pomContent == null || pomContent.trim().isEmpty()) {
            throw new IllegalArgumentException("POM content cannot be empty");
        }

        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new StringReader(pomContent));

            String groupId = model.getGroupId();
            String artifactId = model.getArtifactId();
            String version = model.getVersion();

            // Handle parent inheritance for missing G/V
            if (groupId == null && model.getParent() != null) {
                groupId = model.getParent().getGroupId();
            }
            if (version == null && model.getParent() != null) {
                version = model.getParent().getVersion();
            }

            if (groupId == null || artifactId == null || version == null) {
                throw new IllegalArgumentException("Invalid POM: Missing groupId, artifactId, or version");
            }

            return new MavenCoordinates(groupId, artifactId, version);
        } catch (IOException | XmlPullParserException e) {
            throw new IllegalArgumentException("Failed to parse POM XML: " + e.getMessage(), e);
        }
    }
}
