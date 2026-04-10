package util;

/**
 * Centralizza i messaggi di log durante l'elaborazione dei progetti Jira.
 */
public class ProgressLogger {

    public void logProjectStart(String projectKey) {
        System.out.println("[" + projectKey + "] Inizio elaborazione progetto");
    }

    public void logVersionsLoaded(String projectKey, int count) {
        System.out.println("[" + projectKey + "] Versioni ammesse caricate: " + count);
    }

    public void logPageFetched(String projectKey, int startAt, int fetched, int maxAllowed) {
        System.out.println("[" + projectKey + "] Pagina startAt=" + startAt
                + " — validi finora: " + fetched + "/" + maxAllowed);
    }

    public void logTicketValid(String ticketId) {
        System.out.println("  [OK] " + ticketId);
    }

    public void logTicketDiscarded(String ticketId, String reason) {
        System.out.println("  [SCARTATO] " + ticketId + " — " + reason);
    }

    public void logProjectDone(String projectKey, int count) {
        System.out.println("[" + projectKey + "] Fine elaborazione — ticket validi: " + count);
    }

    public void logGlobalDone(int total) {
        System.out.println("Elaborazione completata. Totale ticket validi: " + total);
    }

    public void logProportionComputed(double p) {
        System.out.println("[PROPORTION] Valore P calcolato: " + String.format("%.4f", p));
    }
}