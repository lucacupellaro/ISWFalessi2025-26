package domain;


import java.time.LocalDateTime;
import java.util.List;

/**
 * Oggetto finale che aggrega tutte le informazioni di un bug ticket:
 * dati anagrafici, fix version e affected versions.
 */

public class BugTicketRecord {
    private final String id;
    private final String projectKey;
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;

    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;
    private final VersionRelation versionRelation;

    public BugTicketRecord(BugTicket ticket, VersionRelation versionRelation,String projectKey) {
        this.id = ticket.getId();
        this.projectKey = projectKey;
        this.creationDate = ticket.getCreationDate();
        this.resolutionDate = ticket.getResolutionDate();

        this.fixVersion = versionRelation.getFixVersion();
        this.affectedVersions = List.copyOf(versionRelation.getAffectedVersions());
        this.versionRelation = versionRelation;
    }

    public String getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }

    public ProjectVersion getFixVersion() { return fixVersion; }
    public List<ProjectVersion> getAffectedVersions() { return affectedVersions; }
    public VersionRelation getVersionRelation() { return versionRelation; }
}