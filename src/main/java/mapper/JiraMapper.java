package mapper;

import domain.BugTicket;
import domain.ProjectVersion;
import domain.VersionRelation;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Converte un JiraIssueDto in oggetti di dominio.
 * Unico punto in cui si conosce la struttura JSON di Jira.
 */
public class JiraMapper {

    private static final DateTimeFormatter JIRA_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    public BugTicket toBugTicket(JiraIssueDto dto) {
        return new BugTicket(
                dto.getId(),
                parseDate(dto.getFields().getCreated()),
                parseDate(dto.getFields().getResolutionDate())
        );
    }

    public VersionRelation toVersionRelation(JiraIssueDto dto) {
        ProjectVersion fixVersion = dto.getFields().getFixVersions() == null
                || dto.getFields().getFixVersions().isEmpty()
                ? null
                : new ProjectVersion(dto.getFields().getFixVersions().get(0).getName(), null);

        List<ProjectVersion> affected = dto.getFields().getAffectedVersions() == null
                ? List.of()
                : dto.getFields().getAffectedVersions().stream()
                  .map(v -> new ProjectVersion(v.getName(), null))
                  .toList();

        return new VersionRelation(fixVersion, affected);
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(raw, JIRA_DATE);
    }
}
