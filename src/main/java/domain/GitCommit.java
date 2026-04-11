package domain;



import java.time.LocalDate;
import java.util.List;

public class GitCommit {

    private final String sha;
    private final String message;
    private final LocalDate date;
    private List<String> touchedPaths;
    private ProjectVersion release;

    public GitCommit(String sha, String message, LocalDate date) {
        this.sha = sha;
        this.message = message;
        this.date = date;
    }

    public String getSha() { return sha; }
    public String getMessage() { return message; }
    public LocalDate getDate() { return date; }
    public List<String> getTouchedPaths() { return touchedPaths; }
    public void setTouchedPaths(List<String> touchedPaths) { this.touchedPaths = touchedPaths; }
    public ProjectVersion getRelease() { return release; }
    public void setRelease(ProjectVersion release) { this.release = release; }
}

