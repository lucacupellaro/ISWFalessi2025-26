package config;

/**
 * Rappresenta la configurazione di un singolo progetto Jira:
 * chiave progetto e query JQL da eseguire.
 */
public class ProjectConfig {

    private String key;
    private String jql;

    public ProjectConfig() {}

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getJql() { return jql; }
    public void setJql(String jql) { this.jql = jql; }
}
