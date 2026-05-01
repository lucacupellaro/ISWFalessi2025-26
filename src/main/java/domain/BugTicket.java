package domain;



import java.time.LocalDateTime;

/**
 * Rappresenta un ticket Jira di tipo Bug nella sua forma essenziale.
 */
public class BugTicket {

    private final String id;
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;
    private final String key;

    public BugTicket(String id, String key, LocalDateTime creationDate, LocalDateTime resolutionDate) {
        this.id = id;
        this.key = key;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
    }

    public String getKey() { return key; }



    public String getId() {
        return id;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getResolutionDate() {
        return resolutionDate;
    }
}