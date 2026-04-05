package domain;

import java.time.LocalDate;

/**
 * Rappresenta una versione di un progetto Jira con nome e data di rilascio.
 */
public class ProjectVersion {

    private final String name;
    private final LocalDate releaseDate; //data ufficiale della relase chiudendeo i fix previsti

    public ProjectVersion(String name, LocalDate releaseDate) {
        this.name = name;
        this.releaseDate = releaseDate;
    }

    public String getName() {
        return name;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }
}
