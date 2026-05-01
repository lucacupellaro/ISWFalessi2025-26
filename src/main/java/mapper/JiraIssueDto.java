package mapper;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO che rispecchia la struttura JSON restituita da Jira per un issue.
 * Popolato automaticamente da Jackson. Non contiene logica.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraIssueDto {

    @JsonProperty("key")
    private String key;

    public String getKey() { return key; }

    @JsonProperty("id")
    private String id;

    @JsonProperty("fields")
    private Fields fields;

    public String getId() { return id; }
    public Fields getFields() { return fields; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Fields {

        @JsonProperty("created")
        private String created;

        @JsonProperty("resolutiondate")
        private String resolutionDate;

        @JsonProperty("fixVersions")
        private List<VersionRef> fixVersions;

        @JsonProperty("versions")
        private List<VersionRef> affectedVersions;

        public String getCreated() { return created; }
        public String getResolutionDate() { return resolutionDate; }
        public List<VersionRef> getFixVersions() { return fixVersions; }
        public List<VersionRef> getAffectedVersions() { return affectedVersions; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VersionRef {

        @JsonProperty("name")
        private String name;

        public String getName() { return name; }
    }
}
