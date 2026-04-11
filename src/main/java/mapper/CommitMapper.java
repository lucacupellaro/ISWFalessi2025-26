package mapper;



import com.fasterxml.jackson.databind.JsonNode;
import domain.GitCommit;
import util.ProgressLogger;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class CommitMapper {

    private final ProgressLogger logger = new ProgressLogger();

    public List<GitCommit> mapCommits(List<JsonNode> rawCommits) {
        List<GitCommit> commits = new ArrayList<>();

        for (JsonNode node : rawCommits) {
            GitCommit commit = mapSingle(node);
            if (commit != null) {
                commits.add(commit);
            }
        }

        return commits;
    }

    private GitCommit mapSingle(JsonNode node) {
        try {
            String sha = node.path("sha").asText(null);
            String message = node.path("commit").path("message").asText(null);
            String dateStr = node.path("commit").path("author").path("date").asText(null);

            if (sha == null || message == null || dateStr == null) {
                logger.logWarning("Commit scartato: campo mancante");
                return null;
            }

            LocalDate date = LocalDate.parse(dateStr.substring(0, 10));
            return new GitCommit(sha, message, date);

        } catch (DateTimeParseException e) {
            logger.logWarning("Commit scartato: data non parsabile");
            return null;
        }
    }
}
