package service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import client.JiraVersionClient;
import domain.ProjectVersion;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Carica le versioni di un progetto, le ordina per data di rilascio
 * e restituisce solo la percentuale configurata (le più vecchie).
 */
public class VersionService {

    private final JiraVersionClient client;
    private final ObjectMapper objectMapper;

    public VersionService(JiraVersionClient client) {
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }

    public List<ProjectVersion> loadVersions(String projectKey, int percentToKeep) {
        String json = client.fetchVersions(projectKey);
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    json, new TypeReference<>() {});

            List<ProjectVersion> all = raw.stream()
                    .filter(v -> v.get("releaseDate") != null)
                    .map(v -> new ProjectVersion(
                            (String) v.get("name"),
                            LocalDate.parse((String) v.get("releaseDate"))))
                    .sorted(Comparator.comparing(ProjectVersion::getReleaseDate))
                    .toList();

            int limit = (int) Math.ceil(all.size() * percentToKeep / 100.0);
            return all.subList(0, limit);

        } catch (Exception e) {
            throw new IllegalStateException("Errore parsing versioni: " + e.getMessage(), e);
        }
    }
}