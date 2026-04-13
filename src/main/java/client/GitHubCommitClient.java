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
import domain.GitCommit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    //Scarica la lista completa di tutti i commit del repository, pagina per pagina. GitHub restituisce massimo 100 commit per chiamata, quindi cicla finché non riceve una pagina vuota.
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

    //Dato lo SHA di un commit, scarica l'intero albero di file del repository a quel punto preciso della storia (?recursive=1). Filtra tenendo solo i file .java ed escludendo i path che contengono /test/.

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

   // Dato lo SHA di un commit specifico, scarica il dettaglio di quel singolo commit e restituisce la lista dei file .java modificati (escludendo i test).
   // Salva anche le diff (additions/deletions) per ogni file nel GitCommit, così non serve richiamare l'endpoint per le metriche LOC.
    public void fetchTouchedPathsAndDiffs(String repoName, GitCommit commit)
            throws IOException, InterruptedException {

        String url = String.format("https://api.github.com/repos/apache/%s/commits/%s",
                repoName, commit.getSha());

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
            commit.setTouchedPaths(new ArrayList<>());
            return;
        }

        JsonNode files = MAPPER.readTree(response.body()).path("files");
        List<String> paths = new ArrayList<>();
        Map<String, List<Integer>> diffs = new java.util.HashMap<>();

        files.forEach(file -> {
            String path = file.path("filename").asText("");
            if (path.endsWith(".java") && !path.contains("/test/")) {
                paths.add(path);
                int added   = file.path("additions").asInt(0);
                int removed = file.path("deletions").asInt(0);
                diffs.put(path, List.of(added, removed));
            }
        });

        commit.setTouchedPaths(paths);
        commit.setFileDiffs(diffs);
    }

    public String fetchFileContent(String repoName, String path, String sha)
            throws IOException, InterruptedException {

        String url = String.format(
                "https://api.github.com/repos/apache/%s/contents/%s?ref=%s",
                repoName, path, sha);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.logWarning("GitHub content API error: " + response.statusCode());
            return null;
        }

        JsonNode node = MAPPER.readTree(response.body());
        String encoded = node.path("content").asText(null);
        if (encoded == null) return null;

        encoded = encoded.replace("\n", "");
        return new String(java.util.Base64.getDecoder().decode(encoded));
    }
}