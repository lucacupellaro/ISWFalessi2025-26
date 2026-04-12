package service;

import domain.BugTicketRecord;
import domain.ProjectVersion;
import domain.VersionRelation;

import java.util.List;

/**
 * Stima la Injection Version (IV) per i ticket che non hanno affected versions,
 * usando il valore di P calcolato da ProportionCalculator.
 *
 * Formula:
 *   IV_index = FV_index - P * (FV_index - OV_index)
 */
public class InjectionVersionEstimator {

    /**
     * Restituisce un nuovo BugTicketRecord con la IV stimata dentro VersionRelation.
     * Se FV o OV non sono trovati nella lista, restituisce il record invariato.
     * Se la IV stimata risulta successiva alla FV, restituisce il record invariato.
     *
     * @param record   ticket senza affected versions
     * @param p        valore di P calcolato da ProportionCalculator
     * @param versions lista ordinata delle versioni ammesse
     * @return nuovo BugTicketRecord con IV stimata, oppure il record originale se non stimabile
     */
    public BugTicketRecord estimateIv(BugTicketRecord record,
                                      double p,
                                      List<ProjectVersion> versions) {
        int fvIndex = indexOf(versions, record.getFixVersionName());
        int ovIndex = indexOf(versions, record.getOpeningVersionName());

        if (fvIndex < 0 || ovIndex < 0) {
            return record;
        }

        int ivIndex = (int) Math.floor(fvIndex - p * (fvIndex - ovIndex));
        ivIndex = Math.max(0, Math.min(ivIndex, versions.size() - 1));

        ProjectVersion estimatedIv = versions.get(ivIndex);
        ProjectVersion fix = record.getVersionRelation().getFixVersion();

        if (estimatedIv.getReleaseDate().isAfter(fix.getReleaseDate())) {
            return record;
        }

        ProjectVersion ov = record.getVersionRelation().getOpeningVersion();
        if (ov != null && estimatedIv.getReleaseDate().isAfter(ov.getReleaseDate())) {
            return record;
        }

        List<ProjectVersion> estimatedAffected = versions.subList(ivIndex, fvIndex);

        VersionRelation enriched = new VersionRelation(
                fix,
                estimatedAffected,
                record.getVersionRelation().getOpeningVersion(),
                estimatedIv
        );

        return new BugTicketRecord(record.toBugTicket(), enriched, record.getProjectKey());
    }

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