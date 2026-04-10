package domain;

import java.util.List;

/**
 * Value object immutabile che raggruppa le relazioni tra versioni per un singolo ticket:
 * fix version, affected versions, opening version e injection version.
 * injectionVersion può essere null se non ancora stimata (ticket senza affected versions).
 */
public class VersionRelation {

    private final ProjectVersion fixVersion;
    private final List<ProjectVersion> affectedVersions;
    private final ProjectVersion openingVersion;
    private final ProjectVersion injectionVersion;

    /**
     * Costruttore usato dal controller durante la fase di raccolta ticket.
     * injectionVersion è null: verrà stimata in seguito da InjectionVersionEstimator.
     */
    public VersionRelation(ProjectVersion fixVersion,
                           List<ProjectVersion> affectedVersions,
                           ProjectVersion openingVersion) {
        this.fixVersion = fixVersion;
        this.affectedVersions = List.copyOf(affectedVersions);
        this.openingVersion = openingVersion;
        this.injectionVersion = null;
    }

    /**
     * Costruttore completo usato dopo la stima della IV tramite Proportion.
     */
    public VersionRelation(ProjectVersion fixVersion,
                           List<ProjectVersion> affectedVersions,
                           ProjectVersion openingVersion,
                           ProjectVersion injectionVersion) {
        this.fixVersion = fixVersion;
        this.affectedVersions = List.copyOf(affectedVersions);
        this.openingVersion = openingVersion;
        this.injectionVersion = injectionVersion;
    }

    public ProjectVersion getFixVersion()             { return fixVersion; }
    public List<ProjectVersion> getAffectedVersions() { return affectedVersions; }
    public ProjectVersion getOpeningVersion()          { return openingVersion; }
    public ProjectVersion getInjectionVersion()        { return injectionVersion; }

    public boolean hasInjectionVersion() {
        return injectionVersion != null;
    }

    public boolean hasAffectedVersions() {
        return !affectedVersions.isEmpty();
    }
}