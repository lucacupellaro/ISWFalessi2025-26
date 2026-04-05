package controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import client.JiraHttpClient;
import client.JiraVersionClient;
import config.AppConfig;
import config.ProjectConfig;
import domain.BugTicket;
import domain.BugTicketRecord;
import domain.ProjectVersion;
import domain.VersionRelation;
import mapper.JiraIssueDto;
import mapper.JiraMapper;
import service.ConsistencyService;
import service.VersionService;
import util.ProgressLogger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;

import java.util.List;

public class AppController {

    private final AppConfig config;
    private final JiraHttpClient issueClient;
    private final JiraMapper mapper;
    private final ConsistencyService consistencyService;
    private final VersionService versionService;
    private final ObjectMapper objectMapper;
    private final ProgressLogger logger = new ProgressLogger();

    public AppController(String baseUrl, String username, String token) {
        this.config = AppConfig.load();
        this.issueClient = new JiraHttpClient(baseUrl, username, token);
        this.mapper = new JiraMapper();
        this.consistencyService = new ConsistencyService();
        this.versionService = new VersionService(new JiraVersionClient(baseUrl, username, token));
        this.objectMapper = new ObjectMapper();
    }

    public List<BugTicketRecord> run() {
        List<BugTicketRecord> results = new ArrayList<>();
        for (ProjectConfig project : config.getProjects()) {
            results.addAll(processProject(project));
        }
        logger.logGlobalDone(results.size());
        return results;
    }

    private List<BugTicketRecord> processProject(ProjectConfig project) {
        List<BugTicketRecord> records = new ArrayList<>();
        int pageSize = config.getSettings().getPageSize();
        int startAt = 0;
        int fetched = 0;

        logger.logProjectStart(project.getKey());

        List<ProjectVersion> allowedVersions = versionService.loadVersions(
                project.getKey(),
                config.getSettings().getMaxVersionsPercent()
        );
        logger.logVersionsLoaded(project.getKey(), allowedVersions.size());

        while (true) {
            String json = issueClient.fetchIssues(project.getJql(), startAt, pageSize);
            JsonNode root = parseJson(json);
            List<JiraIssueDto> dtos = parseIssues(root);

            if (dtos.isEmpty()) {
                break;
            }

            int total = parseTotalAvailable(root);
            int maxAllowed = config.resolvedMaxTickets(total);

            logger.logPageFetched(project.getKey(), startAt, fetched, maxAllowed);

            for (JiraIssueDto dto : dtos) {
                if (fetched >= maxAllowed) {
                    break;
                }

                BugTicket ticket = mapper.toBugTicket(dto); //creo la classe ticket
                VersionRelation versionRelation = mapper.toVersionRelation(dto);  //creo la classe versione
                VersionRelation enriched = enrichVersionRelation(versionRelation, allowedVersions); //il problema potrebbe stare qui
                ProjectVersion associatedRelease = findAssociatedRelease(ticket, allowedVersions);

                if (consistencyService.isValid(ticket, enriched, allowedVersions)) {
                    records.add(new BugTicketRecord(ticket, enriched, associatedRelease, project.getKey()));
                    logger.logTicketValid(ticket.getId());
                    fetched++;
                } else {
                    logger.logTicketDiscarded(ticket.getId(), "dati mancanti o versione non ammessa");
                }
            }

            if (fetched >= maxAllowed || dtos.size() < pageSize) {
                break;
            }
            startAt += pageSize;
        }

        logger.logProjectDone(project.getKey(), records.size());
        return records;
    }

    private ProjectVersion findAssociatedRelease(BugTicket ticket,
                                                 List<ProjectVersion> allowedVersions) {
        if (ticket.getResolutionDate() == null) {
            return null;
        }
        LocalDate resolutionDate = ticket.getResolutionDate().toLocalDate();

        return allowedVersions.stream()
                .filter(v -> v.getReleaseDate() != null)
                .filter(v -> !v.getReleaseDate().isBefore(resolutionDate))
                .min(Comparator.comparing(ProjectVersion::getReleaseDate))
                .orElse(null);
    }

    private VersionRelation enrichVersionRelation(VersionRelation versionRelation,
                                                  List<ProjectVersion> allowedVersions) {
        ProjectVersion enrichedFix = null;
        if (versionRelation.getFixVersion() != null) {
            String fixName = versionRelation.getFixVersion().getName();
            enrichedFix = allowedVersions.stream()
                    .filter(v -> v.getName().equals(fixName))
                    .findFirst()
                    .orElse(versionRelation.getFixVersion());
        }

        List<ProjectVersion> enrichedAffected = versionRelation.getAffectedVersions().stream()
                .map(av -> allowedVersions.stream()
                        .filter(v -> v.getName().equals(av.getName()))
                        .findFirst()
                        .orElse(av))
                .toList();

        return new VersionRelation(enrichedFix, enrichedAffected);
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Errore parsing risposta Jira: " + e.getMessage(), e);
        }
    }

    private List<JiraIssueDto> parseIssues(JsonNode root) {
        try {
            JsonNode issues = root.get("issues");
            if (issues == null || issues.isNull()) {
                return List.of();
            }
            return objectMapper.readValue(
                    issues.toString(),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, JiraIssueDto.class));
        } catch (Exception e) {
            throw new IllegalStateException("Errore parsing issues: " + e.getMessage(), e);
        }
    }

    private int parseTotalAvailable(JsonNode root) {
        JsonNode total = root.get("total");
        return (total != null && !total.isNull()) ? total.asInt() : 0;
    }
}