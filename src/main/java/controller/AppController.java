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
import service.InjectionVersionEstimator;
import service.ProportionCalculator;
import service.VersionService;
import util.ProgressLogger;
import java.util.Comparator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestratore centrale (L2).
 * Unico punto che conosce tutti i layer:
 *   - chiama L0 (client/config) per dati grezzi
 *   - delega a L1 (mapper/service) per trasformazione e validazione
 *   - assembla gli oggetti L3 (domain) e li restituisce
 * Nessuna logica di business vive qui: il controller coordina, non decide.
 */
public class AppController {

    private final AppConfig config;
    private final JiraHttpClient issueClient;
    private final JiraMapper mapper;
    private final ConsistencyService consistencyService;
    private final VersionService versionService;
    private final ObjectMapper objectMapper;
    private final ProportionCalculator proportionCalculator;
    private final InjectionVersionEstimator ivEstimator;
    private final ProgressLogger logger = new ProgressLogger();

    public AppController(String baseUrl, String username, String token) {
        this.config = AppConfig.load();
        this.issueClient = new JiraHttpClient(baseUrl, username, token);
        this.mapper = new JiraMapper();
        this.consistencyService = new ConsistencyService();
        this.versionService = new VersionService(new JiraVersionClient(baseUrl, username, token));
        this.objectMapper = new ObjectMapper();
        this.proportionCalculator = new ProportionCalculator();
        this.ivEstimator = new InjectionVersionEstimator();
    }

    public List<BugTicketRecord> run() {






        List<BugTicketRecord> results = new ArrayList<>();
        List<ProjectVersion> allVersions = new ArrayList<>();

        for (ProjectConfig project : config.getProjects()) {
            List<ProjectVersion> versions = versionService.loadVersions(
                    project.getKey(),
                    config.getSettings().getMaxVersionsPercent()
            );
            allVersions.addAll(versions);
            results.addAll(processProject(project, versions));
        }

        results = new ArrayList<>(applyProportion(results, allVersions));
        logger.logGlobalDone(results.size());
        return results;
    }

    private List<BugTicketRecord> applyProportion(List<BugTicketRecord> records,
                                                  List<ProjectVersion> versions) {
        double p = proportionCalculator.computeP(records, versions);
        logger.logProportionComputed(p);

        return records.stream()
                .map(r -> r.getVersionRelation().hasAffectedVersions()
                        ? r
                        : ivEstimator.estimateIv(r, p, versions))
                .toList();
    }

    private List<BugTicketRecord> processProject(ProjectConfig project,
                                                 List<ProjectVersion> allowedVersions) {
        List<BugTicketRecord> records = new ArrayList<>();
        int pageSize = config.getSettings().getPageSize();
        int startAt = 0;
        int fetched = 0;

        logger.logProjectStart(project.getKey());
        logger.logVersionsLoaded(project.getKey(), allowedVersions.size());

        boolean hasMore = true;
        while (hasMore) {
            String json = issueClient.fetchIssues(project.getJql(), startAt, pageSize);
            JsonNode root = parseJson(json);
            List<JiraIssueDto> dtos = parseIssues(root);

            if (dtos.isEmpty()) {
                hasMore = false;
            } else {
                int total = parseTotalAvailable(root);
                int maxAllowed = config.resolvedMaxTickets(total);

                logger.logPageFetched(project.getKey(), startAt, fetched, maxAllowed);

                for (int i = 0; i < dtos.size() && fetched < maxAllowed; i++) {
                    fetched += processTicket(dtos.get(i), allowedVersions, project.getKey(), records);
                }

                hasMore = fetched < maxAllowed && dtos.size() >= pageSize;
                startAt += pageSize;
            }
        }

        logger.logProjectDone(project.getKey(), records.size());
        return records;
    }

    private int processTicket(JiraIssueDto dto,
                              List<ProjectVersion> allowedVersions,
                              String projectKey,
                              List<BugTicketRecord> records) {
        BugTicket ticket = mapper.toBugTicket(dto);
        ProjectVersion rawFix = mapper.toFixVersion(dto);
        List<ProjectVersion> rawAffected = mapper.toAffectedVersions(dto);

        VersionRelation versionRelation = buildVersionRelation(
                rawFix,
                rawAffected,
                allowedVersions,
                ticket.getCreationDate() != null ? ticket.getCreationDate().toLocalDate() : null
        );

        String reason = consistencyService.getDiscardReason(ticket, versionRelation, allowedVersions);
        if (reason == null) {
            records.add(new BugTicketRecord(ticket, versionRelation, projectKey));
            logger.logTicketValid(ticket.getId());
            return 1;
        }
        logger.logTicketDiscarded(ticket.getId(), reason);
        return 0;
    }

    private VersionRelation buildVersionRelation(ProjectVersion rawFix,
                                                 List<ProjectVersion> rawAffected,
                                                 List<ProjectVersion> allowedVersions,
                                                 LocalDate creationDate) {
        // arricchisce fix con releaseDate ufficiale
        ProjectVersion enrichedFix = enrich(rawFix, allowedVersions);

        // arricchisce tutte le affected con releaseDate ufficiale
        // e le ordina cronologicamente per releaseDate
        List<ProjectVersion> enrichedAffected = rawAffected.stream()
                .map(av -> enrich(av, allowedVersions))
                .filter(av -> av.getReleaseDate() != null)
                .sorted(Comparator.comparing(ProjectVersion::getReleaseDate))
                .toList();

        ProjectVersion openingVersion = creationDate != null
                ? versionService.findOpeningVersion(creationDate, allowedVersions)
                : null;

        // IV nota: prima affected version (già ordinata cronologicamente)
        ProjectVersion injectionVersion = enrichedAffected.isEmpty()
                ? null
                : enrichedAffected.get(0);

        return new VersionRelation(enrichedFix, enrichedAffected, openingVersion, injectionVersion);
    }

    private ProjectVersion enrich(ProjectVersion raw, List<ProjectVersion> allowedVersions) {
        if (raw == null) {
            return null;
        }
        return allowedVersions.stream()
                .filter(v -> v.getName().equals(raw.getName()))
                .findFirst()
                .orElse(raw);
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