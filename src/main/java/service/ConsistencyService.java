package service;



import domain.BugTicket;
import domain.VersionRelation;

/**
 * Valida la consistenza di un ticket prima che arrivi al controller.
 * Scarta ticket con dati mancanti o incoerenti.
 */
public class ConsistencyService {

    public boolean isValid(BugTicket ticket, VersionRelation versionRelation) {
        if (ticket.getCreationDate() == null || ticket.getResolutionDate() == null) {
            return false;
        }
        if (ticket.getResolutionDate().isBefore(ticket.getCreationDate())) {
            return false;
        }
        if (versionRelation.getFixVersion() == null) {
            return false;
        }
        return true;
    }
}