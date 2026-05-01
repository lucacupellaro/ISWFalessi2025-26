package service;

import domain.BugTicketRecord;
import domain.ProjectVersion;

import java.util.List;

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

    /**
     * Calcola P come media dei valori individuali di tutti i ticket
     * che hanno almeno una affected version (IV nota).
     *
     * @param records  lista completa di BugTicketRecord
     * @param versions lista ordinata delle versioni ammesse (indice = posizione cronologica)
     * @return valore medio di P, oppure 0.0 se nessun ticket ha IV nota
     */
    public double computeP(List<BugTicketRecord> records, List<ProjectVersion> versions) {
        List<BugTicketRecord> withIv = records.stream()
                .filter(r -> r.getVersionRelation().hasAffectedVersions())
                .toList();

        if (withIv.isEmpty()) {
            return 0.0;
        }

        double sum = 0.0;
        int count = 0;

        for (BugTicketRecord record : withIv) {
            Double p = computeSingleP(record, versions);

            if (p != null) {
                sum += p;
                count++;
               // System.out.println("Al ticket i:"+count+", il valore di p: "+p+" il p medio fino al ticket i è:"+sum/count);
            }
        }

        return count > 0 ? sum / count : 0.0;
    }

    /**
     * Calcola P per un singolo ticket.
     * Restituisce null se gli indici non sono validi o il denominatore è zero.
     */
    private Double computeSingleP(BugTicketRecord record, List<ProjectVersion> versions) {
        int fvIndex = indexOf(versions, record.getFixVersionName());
        int ovIndex = indexOf(versions, record.getOpeningVersionName());
        int ivIndex = indexOf(versions, record.getInjectionVersionName());

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