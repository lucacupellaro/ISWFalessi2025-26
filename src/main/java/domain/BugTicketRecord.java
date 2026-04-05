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
    private final ProjectVersion associatedRelease;
    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;

    public BugTicketRecord(BugTicket ticket, VersionRelation versionRelation,
                           ProjectVersion associatedRelease, String projectKey) {
        this.id = ticket.getId();
        this.projectKey = projectKey;
        this.creationDate = ticket.getCreationDate();
        this.resolutionDate = ticket.getResolutionDate();
        this.associatedRelease = associatedRelease;
        this.fixVersion = versionRelation.getFixVersion();
        this.affectedVersions = List.copyOf(versionRelation.getAffectedVersions());
    }

    public String getId() { return id; }
    public String getProjectKey() { return projectKey; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public LocalDateTime getResolutionDate() { return resolutionDate; }
    public ProjectVersion getAssociatedRelease() { return associatedRelease; }
    public ProjectVersion getFixVersion() { return fixVersion; }
    public List<ProjectVersion> getAffectedVersions() { return affectedVersions; }
}