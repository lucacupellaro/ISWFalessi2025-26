package service;

import domain.BugTicketRecord;
import domain.ProjectVersion;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calcola il valore di P (Proportion) dalla storia dei ticket che hanno
 * le affected versions, e quindi la IV è nota.
 *
 * Formula per ogni ticket:
 *   P = (FV_index - IV_index) / (FV_index - OV_index)
 *
 * P finale = media dei P calcolati su tutti i ticket con IV nota.
 */
public class ProportionCalculator {

    private static final Logger logger = Logger.getLogger(ProportionCalculator.class.getName());

    /**
     * Calcola P come media dei valori individuali di tutti i ticket
     * che hanno almeno una affected version (IV nota).
     * Stampa il valore incrementale di P ad ogni ticket valido.
     *
     * @param ticketRecords  lista completa di BugTicketRecord
     * @param versions lista ordinata delle versioni ammesse (indice = posizione cronologica)
     * @return valore medio di P, oppure 0.0 se nessun ticket ha IV nota
     */
    public double computeP(List<BugTicketRecord> ticketRecords, List<ProjectVersion> versions) {
        List<BugTicketRecord> withIv = ticketRecords.stream()
                .filter(r -> r.getVersionRelation().hasAffectedVersions())
                .toList();

        if (withIv.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;

        for (BugTicketRecord ticketRecord : withIv) {
            Double p = computeSingleP(ticketRecord, versions);

            if (p != null) {
                sum += p;
                count++;
                double currentP = sum / count;
                logger.log(Level.INFO, "[PROPORTION] Ticket {0}/{1} — P = {2}",
                        new Object[]{count, withIv.size(), String.format("%.4f", currentP)});
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Calcola P per un singolo ticket.
     * Restituisce null se gli indici non sono validi o il denominatore è zero.
     */
    private Double computeSingleP(BugTicketRecord ticketRecord, List<ProjectVersion> versions) {
        int fvIndex = indexOf(versions, ticketRecord.getFixVersionName());
        int ovIndex = indexOf(versions, ticketRecord.getOpeningVersionName());
        int ivIndex = indexOf(versions, ticketRecord.getInjectionVersionName());

        if (fvIndex < 0 || ovIndex < 0 || ivIndex < 0) {
            return null;
        }

        int denominator = fvIndex - ovIndex;
        if (denominator == 0) {
            return null;
        }

        return (double) (fvIndex - ivIndex) / denominator;
    }

    // posizione numerica (indice) di una versione nella lista ordinata cronologicamente.
    private int indexOf(List<ProjectVersion> versions, String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}