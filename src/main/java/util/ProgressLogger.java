package util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizza i messaggi di log durante l'elaborazione dei progetti Jira.
 */
public class ProgressLogger {

    private static final Logger logger = Logger.getLogger(ProgressLogger.class.getName());

    public void logProjectStart(String projectKey) {
        logger.log(Level.INFO, "[{0}] Inizio elaborazione progetto", projectKey);
    }

    public void logVersionsLoaded(String projectKey, int count) {
        logger.log(Level.INFO, "[{0}] Versioni ammesse caricate: {1}", new Object[]{projectKey, count});
    }

    public void logPageFetched(String projectKey, int startAt, int fetched, int maxAllowed) {
        logger.log(Level.INFO, "[{0}] Pagina startAt={1} — validi finora: {2}/{3}",
                new Object[]{projectKey, startAt, fetched, maxAllowed});
    }

    public void logTicketValid(String ticketId) {
        logger.log(Level.INFO, "  [OK] {0}", ticketId);
    }

    public void logTicketDiscarded(String ticketId, String reason) {
        logger.log(Level.INFO, "  [SCARTATO] {0} — {1}", new Object[]{ticketId, reason});
    }

    public void logProjectDone(String projectKey, int count) {
        logger.log(Level.INFO, "[{0}] Fine elaborazione — ticket validi: {1}", new Object[]{projectKey, count});
    }

    public void logGlobalDone(int total) {
        logger.log(Level.INFO, "Elaborazione completata. Totale ticket validi: {0}", total);
    }

    public void logProportionComputed(double p) {
        logger.log(Level.INFO, "[PROPORTION] Valore P calcolato: {0}", String.format("%.4f", p));
    }

    public void logInfo(String message) {
        logger.log(Level.INFO, "[INFO] {0}", message);
    }

    public void logWarning(String message) {
        logger.log(Level.WARNING, "[WARN] {0}", message);
    }
}