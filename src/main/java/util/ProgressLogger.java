package util;

import java.util.logging.Logger;

/**
 * Centralizza i messaggi di log durante l'elaborazione dei progetti Jira.
 */
public class ProgressLogger {

    private static final Logger logger = Logger.getLogger(ProgressLogger.class.getName());

    public void logProjectStart(String projectKey) {
        logger.info("[" + projectKey + "] Inizio elaborazione progetto");
    }

    public void logVersionsLoaded(String projectKey, int count) {
        logger.info("[" + projectKey + "] Versioni ammesse caricate: " + count);
    }

    public void logPageFetched(String projectKey, int startAt, int fetched, int maxAllowed) {
        logger.info("[" + projectKey + "] Pagina startAt=" + startAt
                + " — validi finora: " + fetched + "/" + maxAllowed);
    }

    public void logTicketValid(String ticketId) {
        logger.info("  [OK] " + ticketId);
    }

    public void logTicketDiscarded(String ticketId, String reason) {
        logger.info("  [SCARTATO] " + ticketId + " — " + reason);
    }

    public void logProjectDone(String projectKey, int count) {
        logger.info("[" + projectKey + "] Fine elaborazione — ticket validi: " + count);
    }

    public void logGlobalDone(int total) {
        logger.info("Elaborazione completata. Totale ticket validi: " + total);
    }

    public void logProportionComputed(double p) {
        logger.info("[PROPORTION] Valore P calcolato: " + String.format("%.4f", p));
    }

    public void logInfo(String message) {
        logger.info("[INFO] " + message);
    }

    public void logWarning(String message) {
        logger.warning("[WARN] " + message);
    }
}