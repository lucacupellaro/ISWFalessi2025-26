package domain;



import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitCommit {

    private final String sha;
    private final String message;
    private final LocalDate date;
    private List<String> touchedPaths;  //lista delle classi toccate da quel commit
    private Map<String, List<Integer>> fileDiffs = new HashMap<>(); // path -> [added, removed]
    private ProjectVersion release;
    private String author;

    public GitCommit(String sha, String message, LocalDate date) {
        this.sha = sha;
        this.message = message;
        this.date = date;
    }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getSha() { return sha; }
    public String getMessage() { return message; }
    public LocalDate getDate() { return date; }
    public List<String> getTouchedPaths() { return touchedPaths; }
    public void setTouchedPaths(List<String> touchedPaths) { this.touchedPaths = touchedPaths; }
    public ProjectVersion getRelease() { return release; }
    public void setRelease(ProjectVersion release) { this.release = release; }

    public Map<String, List<Integer>> getFileDiffs() { return fileDiffs; }
    public void setFileDiffs(Map<String, List<Integer>> fileDiffs) { this.fileDiffs = fileDiffs; }
}

