package service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import client.JiraVersionClient;
import domain.ProjectVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Risolve i nomi di versione in oggetti ProjectVersion completi di release date.
 * Costruisce la mappa versioni una volta sola per progetto.
 */
public class VersionService {

    private final JiraVersionClient client;
    private final ObjectMapper objectMapper;

    public VersionService(JiraVersionClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    public List<ProjectVersion> loadVersions(String projectKey) {
        String json = client.fetchVersions(projectKey);
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json, new TypeReference<>() {});
            return raw.stream()
                    .map(v -> new ProjectVersion(
                            (String) v.get("name"),
                            v.get("releaseDate") != null
                                    ? LocalDate.parse((String) v.get("releaseDate"))
                                    : null))
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Errore parsing versioni: " + e.getMessage(), e);
        }
    }
}

