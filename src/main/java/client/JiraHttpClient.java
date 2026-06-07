package client;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Esegue chiamate HTTP verso Jira per recuperare i ticket tramite JQL.
 * Gestisce la paginazione. Restituisce JSON grezzo.
 */
public class JiraHttpClient {

    private static final Logger logger = Logger.getLogger(JiraHttpClient.class.getName());
    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;

    public JiraHttpClient(String baseUrl, String username, String token) {
        this.baseUrl = baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + token).getBytes());
        this.httpClient = HttpClient.newHttpClient();
    }

    public String fetchIssues(String jql, int startAt, int maxResults) {
        logger.log(Level.FINE, "[HTTP] Chiamata Jira issues — startAt={0} maxResults={1}",
                new Object[]{startAt, maxResults});
        String url = baseUrl + "/rest/api/2/search"
                + "?jql=" + URLEncoder.encode(jql, StandardCharsets.UTF_8)
                + "&startAt=" + startAt
                + "&maxResults=" + maxResults
                + "&fields=id,created,resolutiondate,fixVersions,versions";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json");

        if (authHeader != null && !authHeader.equals("Basic Og==")) {
            requestBuilder.header("Authorization", authHeader);
        }

        HttpRequest request = requestBuilder.GET().build();

        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Errore chiamata Jira issues: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Errore chiamata Jira issues: " + e.getMessage(), e);
        }
    }
}