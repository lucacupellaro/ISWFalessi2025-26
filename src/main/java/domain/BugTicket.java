package domain;



import java.time.LocalDateTime;

/**
 * Rappresenta un ticket Jira di tipo Bug nella sua forma essenziale.
 */
public class BugTicket {

    private final String id;
    private final LocalDateTime creationDate;
    private final LocalDateTime resolutionDate;

    public BugTicket(String id, LocalDateTime creationDate, LocalDateTime resolutionDate) {
        this.id = id;
        this.creationDate = creationDate;
        this.resolutionDate = resolutionDate;
    }

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