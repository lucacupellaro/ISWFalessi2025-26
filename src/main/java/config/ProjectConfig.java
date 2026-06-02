package config;

/**
 * Rappresenta la configurazione di un singolo progetto Jira:
 * chiave progetto e query JQL da eseguire.
 */
public class ProjectConfig {

    private String key;
    private String jql;
    private String repoName;

    public String getRepoName() { return repoName; }
    public void setRepoName(String repoName) { this.repoName = repoName; }

    // Costruttore vuoto richiesto da Jackson per la deserializzazione YAML
    public ProjectConfig() {
        // Empty constructor required by Jackson for YAML deserialization
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getJql() { return jql; }
    public void setJql(String jql) { this.jql = jql; }
}
