package domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Oggetto finale immutabile che aggrega tutte le informazioni di un bug ticket:
 * dati anagrafici, fix version, affected versions, opening version e injection version.
 * Espone metodi delegati flat per i dati delle versioni,
 * così i consumatori (es. PrintOnCsv) non dipendono da ProjectVersion.
 */
public class BugTicketRecord {

    private final String id;
    private final String projectKey;
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;
    private final VersionRelation versionRelation;

    public BugTicketRecord(BugTicket ticket, VersionRelation versionRelation, String projectKey) {
        this.id = ticket.getId();
        this.projectKey = projectKey;
        this.creationDate = ticket.getCreationDate();
        this.resolutionDate = ticket.getResolutionDate();
        this.versionRelation = versionRelation;
    }

    // --- Dati anagrafici ---

    public String getId()                    { return id; }
    public String getProjectKey()            { return projectKey; }
    public LocalDateTime getCreationDate()   { return creationDate; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }

    /**
     * Ricostruisce il BugTicket originale — usato da InjectionVersionEstimator
     * per creare un nuovo BugTicketRecord con IV aggiornata.
     */
    public BugTicket toBugTicket() {
        return new BugTicket(id, creationDate, resolutionDate);
    }

    // --- Delegati fix version ---

    public String getFixVersionName() {
        ProjectVersion fix = versionRelation.getFixVersion();
        return fix != null ? fix.getName() : null;
    }

    public String getFixVersionReleaseDate() {
        ProjectVersion fix = versionRelation.getFixVersion();
        return (fix != null && fix.getReleaseDate() != null)
                ? fix.getReleaseDate().toString() : null;
    }

    // --- Delegati affected versions ---

    public int getAffectedVersionsCount() {
        return versionRelation.getAffectedVersions().size();
    }

    public List<String> getAffectedVersionNames() {
        return versionRelation.getAffectedVersions().stream()
                .map(ProjectVersion::getName)
                .toList();
    }

    // --- Delegati opening version ---

    public String getOpeningVersionName() {
        ProjectVersion ov = versionRelation.getOpeningVersion();
        return ov != null ? ov.getName() : null;
    }

    public String getOpeningVersionReleaseDate() {
        ProjectVersion ov = versionRelation.getOpeningVersion();
        return (ov != null && ov.getReleaseDate() != null)
                ? ov.getReleaseDate().toString() : null;
    }

    // --- Delegati injection version ---

    public String getInjectionVersionName() {
        ProjectVersion iv = versionRelation.getInjectionVersion();
        return iv != null ? iv.getName() : null;
    }

    public String getInjectionVersionReleaseDate() {
        ProjectVersion iv = versionRelation.getInjectionVersion();
        return (iv != null && iv.getReleaseDate() != null)
                ? iv.getReleaseDate().toString() : null;
    }

    // --- Accesso strutturato (solo per AppController e domain) ---

    public VersionRelation getVersionRelation() { return versionRelation; }
}