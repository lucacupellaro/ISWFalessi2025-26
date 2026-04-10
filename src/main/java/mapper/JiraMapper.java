package mapper;

import domain.BugTicket;
import domain.ProjectVersion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Converte un JiraIssueDto in oggetti di dominio primitivi (L1 → L3).
 * Unico punto in cui si conosce la struttura JSON di Jira.
 * Gestisce i 4 casi di combinazione fix/affected versions.
 * Non assembla mai VersionRelation — quella responsabilità appartiene al controller (L2).
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
     * Restituisce la fix version corretta in base ai 4 casi:
     * - fix = 1 → quella unica
     * - fix > 1 → la più recente (le altre vanno in affected)
     */
    public ProjectVersion toFixVersion(JiraIssueDto dto) {
        List<JiraIssueDto.VersionRef> fixVersions = dto.getFields().getFixVersions();
        if (fixVersions == null || fixVersions.isEmpty()) {
            return null;
        }
        if (fixVersions.size() == 1) {
            return new ProjectVersion(fixVersions.get(0).getName(), null);
        }
        // fix > 1 → prende quella con nome che verrà arricchito con releaseDate dal controller
        // per ora restituiamo tutte e il controller sceglierà la più recente dopo l'arricchimento
        // usiamo l'ultima nella lista come approssimazione — verrà corretta in buildVersionRelation
        return new ProjectVersion(fixVersions.get(fixVersions.size() - 1).getName(), null);
    }

    /**
     * Restituisce le affected versions corrette in base ai 4 casi:
     * - fix = 1, affected = 0 → lista vuota
     * - fix > 1, affected = 0 → fix extra (esclusa la più recente)
     * - fix = 1, affected > 0 → affected originali
     * - fix > 1, affected > 0 → affected originali + fix extra (esclusa la più recente)
     * L'ordinamento cronologico viene fatto nel controller dopo l'arricchimento con releaseDate.
     */
    public List<ProjectVersion> toAffectedVersions(JiraIssueDto dto) {
        List<JiraIssueDto.VersionRef> fixVersions = dto.getFields().getFixVersions();
        List<JiraIssueDto.VersionRef> affectedVersions = dto.getFields().getAffectedVersions();

        List<ProjectVersion> affected = new ArrayList<>();

        // aggiunge le affected originali
        if (affectedVersions != null) {
            affectedVersions.stream()
                    .map(v -> new ProjectVersion(v.getName(), null))
                    .forEach(affected::add);
        }

        // se fix > 1, aggiunge le fix extra (tutte tranne l'ultima) nelle affected
        if (fixVersions != null && fixVersions.size() > 1) {
            fixVersions.subList(0, fixVersions.size() - 1).stream()
                    .map(v -> new ProjectVersion(v.getName(), null))
                    .forEach(affected::add);
        }

        return affected;
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(raw, JIRA_DATE);
    }
}