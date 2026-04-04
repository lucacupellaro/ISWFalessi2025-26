package controller;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import client.JiraHttpClient;
import client.JiraVersionClient;
import config.AppConfig;
import config.ProjectConfig;
import domain.BugTicket;
import domain.BugTicketRecord;
import domain.VersionRelation;
import mapper.JiraIssueDto;
import mapper.JiraMapper;
import service.ConsistencyService;
import service.VersionService;

import java.util.ArrayList;
import java.util.List;

/**
 * Punto centrale dell'applicazione. Coordina tutti i layer,
 * crea le entità di dominio e le assembla in BugTicketRecord.
 */
public class AppController {

    private final AppConfig config;
    private final JiraHttpClient issueClient;
    private final JiraMapper mapper;
    private final ConsistencyService consistencyService;
    private final VersionService versionService;
    private final ObjectMapper objectMapper;

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

        return results;
    }

    private List<BugTicketRecord> processProject(ProjectConfig project) {
        List<BugTicketRecord> records = new ArrayList<>();
        int pageSize = config.getSettings().getPageSize();
        int startAt = 0;
        int fetched = 0;

        while (true) {
            String json = issueClient.fetchIssues(project.getJql(), startAt, pageSize);
            List<JiraIssueDto> dtos = parseIssues(json);

            if (dtos.isEmpty()) {
                break;
            }

            int total = parseTotalAvailable(json);
            int maxAllowed = config.resolvedMaxTickets(total);

            for (JiraIssueDto dto : dtos) {
                if (fetched >= maxAllowed) {
                    break;
                }

                BugTicket ticket = mapper.toBugTicket(dto);
                VersionRelation versionRelation = mapper.toVersionRelation(dto);

                if (consistencyService.isValid(ticket, versionRelation)) {
                    records.add(new BugTicketRecord(ticket, versionRelation));
                    fetched++;
                }
            }

            if (fetched >= maxAllowed || dtos.size() < pageSize) {
                break;
            }

            startAt += pageSize;
        }

        return records;
    }

    private List<JiraIssueDto> parseIssues(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode issues = root.get("issues");
            return objectMapper.readValue(
                    issues.toString(),
                    objectMapper.getTypeFactory()
                            .constructCollectionType(List.class, JiraIssueDto.class));
        } catch (Exception e) {
            throw new IllegalStateException("Errore parsing issues: " + e.getMessage(), e);
        }
    }

    private int parseTotalAvailable(String json) {
        try {
            return objectMapper.readTree(json).get("total").asInt();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void main(String[] args) {
        String baseUrl = "https://yourorg.atlassian.net";
        String username = "your@email.com";
        String token = "your-api-token";

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        System.out.println("Ticket validi recuperati: " + records.size());
        records.forEach(r -> System.out.println(r.getId()
                + " | fix: " + (r.getFixVersion() != null ? r.getFixVersion().getName() : "n/a")
                + " | affected: " + r.getAffectedVersions().size()));
    }
}
