package config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.InputStream;
import java.util.List;

/**
 * Carica e espone tutta la configurazione applicativa da projects.yml.
 * Unico punto di accesso ai parametri operativi.
 */
public class AppConfig {

    private Settings settings;
    private List<ProjectConfig> projects;

    public AppConfig() {}

    public static AppConfig load() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = AppConfig.class
                .getClassLoader()
                .getResourceAsStream("projects.yml")) {
            return mapper.readValue(is, AppConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile caricare projects.yml", e);
        }
    }

    public int resolvedMaxTickets(int totalAvailable) {
        if (settings.getMaxTicketsPercent() > 0) {
            return (int) Math.min(totalAvailable,
                    Math.ceil(totalAvailable * settings.getMaxTicketsPercent() / 100.0));
        }
        return settings.getMaxTicketsPerProject();
    }

    public int resolvedMaxVersions(int totalAvailable) {
        if (settings.getMaxVersionsPercent() > 0) {
            return (int) Math.min(totalAvailable,
                    Math.ceil(totalAvailable * settings.getMaxVersionsPercent() / 100.0));
        }
        return settings.getMaxVersionsPerProject();
    }

    public Settings getSettings() { return settings; }
    public void setSettings(Settings settings) { this.settings = settings; }

    public List<ProjectConfig> getProjects() { return projects; }
    public void setProjects(List<ProjectConfig> projects) { this.projects = projects; }

    /**
     * Parametri operativi globali: limiti ticket, versioni, paginazione.
     */
    public static class Settings {
        private int maxTicketsPerProject;
        private int maxTicketsPercent;
        private int maxVersionsPerProject;
        private int maxVersionsPercent;
        private int pageSize;

        public Settings() {}

        public int getMaxTicketsPerProject() { return maxTicketsPerProject; }
        public void setMaxTicketsPerProject(int v) { this.maxTicketsPerProject = v; }

        public int getMaxTicketsPercent() { return maxTicketsPercent; }
        public void setMaxTicketsPercent(int v) { this.maxTicketsPercent = v; }

        public int getMaxVersionsPerProject() { return maxVersionsPerProject; }
        public void setMaxVersionsPerProject(int v) { this.maxVersionsPerProject = v; }

        public int getMaxVersionsPercent() { return maxVersionsPercent; }
        public void setMaxVersionsPercent(int v) { this.maxVersionsPercent = v; }

        public int getPageSize() { return pageSize; }
        public void setPageSize(int v) { this.pageSize = v; }
    }
}
