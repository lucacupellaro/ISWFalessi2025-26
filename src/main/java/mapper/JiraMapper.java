package mapper;

import domain.BugTicket;
import domain.ProjectVersion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Converte un JiraIssueDto in oggetti di dominio primitivi (L1 → L3).
 * Unico punto in cui si conosce la struttura JSON di Jira.
 * Non assembla mai VersionRelation — quella responsabilità appartiene al controller (L2),
 * che ha accesso a tutte le informazioni necessarie (allowedVersions, creationDate).
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

    /**
     * Estrae la fix version grezza dal DTO (senza releaseDate).
     * Il controller provvederà ad arricchirla con i dati ufficiali.
     * Restituisce null se il ticket non ha fix version.
     */
    public ProjectVersion toFixVersion(JiraIssueDto dto) {
        List<JiraIssueDto.VersionRef> fixVersions = dto.getFields().getFixVersions();
        if (fixVersions == null || fixVersions.isEmpty()) {
            return null;
        }
        return new ProjectVersion(fixVersions.get(0).getName(), null);
    }

    /**
     * Estrae le affected versions grezze dal DTO (senza releaseDate).
     * Il controller provvederà ad arricchirle con i dati ufficiali.
     */
    public List<ProjectVersion> toAffectedVersions(JiraIssueDto dto) {
        List<JiraIssueDto.VersionRef> versions = dto.getFields().getAffectedVersions();
        if (versions == null) {
            return List.of();
        }
        return versions.stream()
                .map(v -> new ProjectVersion(v.getName(), null))
                .toList();
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(raw, JIRA_DATE);
    }
}
