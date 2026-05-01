package client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Esegue la chiamata HTTP verso Jira per recuperare le versioni di un progetto.
 * Restituisce JSON grezzo.
 */
public class JiraVersionClient {

    private static final Logger logger = Logger.getLogger(JiraVersionClient.class.getName());
    private final String baseUrl;
    private final String authHeader;
    private final HttpClient httpClient;

    public JiraVersionClient(String baseUrl, String username, String token) {
        this.baseUrl = baseUrl;
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + token).getBytes());
        this.httpClient = HttpClient.newHttpClient();
    }

    public String fetchVersions(String projectKey) {
        logger.log(Level.INFO, "[HTTP] Chiamata Jira versioni — progetto: {0}", projectKey);


        String url = baseUrl + "/rest/api/2/project/" + projectKey + "/versions";

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
        } catch (Exception e) {
            throw new IllegalStateException("Errore chiamata Jira versions: " + e.getMessage(), e);
        }
    }
}
