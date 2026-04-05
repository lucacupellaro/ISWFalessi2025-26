package domain;

import java.util.List;

/**
 * Tiene le relazioni tra versioni per un singolo ticket:
 * fix version e affected versions.
 */
public class VersionRelation {

    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;
    private ProjectVersion openingVersion;

    public VersionRelation(ProjectVersion fixVersion, List<ProjectVersion> affectedVersions) {
        this.fixVersion = fixVersion;
        this.affectedVersions = List.copyOf(affectedVersions);
    }


    public VersionRelation(ProjectVersion fixVersion, List<ProjectVersion> affectedVersions, ProjectVersion openingVersion) {
        this.fixVersion = fixVersion;
        this.affectedVersions = List.copyOf(affectedVersions);
        this.openingVersion = openingVersion;
    }

    public ProjectVersion getFixVersion() {
        return fixVersion;
    }


    public ProjectVersion getOpeningVersion() {
        return openingVersion;
    }

    public List<ProjectVersion> getAffectedVersions() {
        return affectedVersions;
    }
}