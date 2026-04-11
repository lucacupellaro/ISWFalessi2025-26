package client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import config.ProjectConfig;
import util.ProgressLogger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GitHubCommitClient {

    private static final String BASE_URL = "https://api.github.com/repos/apache/%s/commits";
    private static final int PER_PAGE = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String token;
    private final ProgressLogger logger;

    public GitHubCommitClient(String token, ProgressLogger logger) {
        this.httpClient = HttpClient.newHttpClient();
        this.token = token;
        this.logger = logger;
    }

    public List<JsonNode> fetchAllCommits(ProjectConfig projectConfig) throws IOException, InterruptedException {
        List<JsonNode> allCommits = new ArrayList<>();
        int page = 1;

        while (true) {
            String url = String.format(BASE_URL + "?per_page=%d&page=%d",
                    projectConfig.getRepoName(), PER_PAGE, page);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.logWarning("GitHub API error: " + response.statusCode());
                break;
            }

            JsonNode commits = MAPPER.readTree(response.body());

            if (!commits.isArray() || commits.isEmpty()) {
                break;
            }

            commits.forEach(allCommits::add);
            logger.logInfo("Fetched page " + page + " — total commits so far: " + allCommits.size());
            page++;
        }

        return allCommits;
    }

    public List<String> fetchJavaClasses(String repoName, String sha)
            throws IOException, InterruptedException {

        String url = String.format("https://api.github.com/repos/apache/%s/git/trees/%s?recursive=1",
                repoName, sha);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.logWarning("GitHub tree API error: " + response.statusCode());
            return new ArrayList<>();
        }

        JsonNode tree = MAPPER.readTree(response.body()).path("tree");
        List<String> classes = new ArrayList<>();

        tree.forEach(node -> {
            String path = node.path("path").asText("");
            if (path.endsWith(".java") && !path.contains("/test/")) {
                classes.add(path);
            }
        });

        return classes;
    }

    public List<String> fetchTouchedPaths(String repoName, String sha)
            throws IOException, InterruptedException {

        String url = String.format("https://api.github.com/repos/apache/%s/commits/%s",
                repoName, sha);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.logWarning("GitHub commit detail API error: " + response.statusCode());
            return new ArrayList<>();
        }

        JsonNode files = MAPPER.readTree(response.body()).path("files");
        List<String> paths = new ArrayList<>();

        files.forEach(file -> {
            String path = file.path("filename").asText("");
            if (path.endsWith(".java") && !path.contains("/test/")) {
                paths.add(path);
            }
        });

        return paths;
    }
}