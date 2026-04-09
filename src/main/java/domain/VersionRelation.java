package domain;

import java.util.List;

/**
 * Value object immutabile che raggruppa le relazioni tra versioni per un singolo ticket:
 * fix version, affected versions e opening version.
 * Costruito solo quando tutti i campi sono disponibili — nessuno stato parziale.
 */
public class VersionRelation {

    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;
    private final ProjectVersion openingVersion;

    public VersionRelation(ProjectVersion fixVersion,
                           List<ProjectVersion> affectedVersions,
                           ProjectVersion openingVersion) {
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