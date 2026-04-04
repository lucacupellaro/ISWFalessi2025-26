package domain;

import java.util.List;

/**
 * Tiene le relazioni tra versioni per un singolo ticket:
 * fix version e affected versions.
 */
public class VersionRelation {

    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;

    public VersionRelation(ProjectVersion fixVersion, List<ProjectVersion> affectedVersions) {
        this.fixVersion = fixVersion;
        this.affectedVersions = List.copyOf(affectedVersions);
    }

    public ProjectVersion getFixVersion() {
        return fixVersion;
    }

    public List<ProjectVersion> getAffectedVersions() {
        return affectedVersions;
    }
}