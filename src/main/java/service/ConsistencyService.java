package service;

import domain.BugTicket;
import domain.ProjectVersion;
import domain.VersionRelation;

import java.util.List;

/**
 * Valida la consistenza di un ticket e verifica che la fix version
 * rientri nelle versioni ammesse (escluso l'ultimo 66%).
 */
public class ConsistencyService {

    public boolean isValid(BugTicket ticket,
                           VersionRelation versionRelation,
                           List<ProjectVersion> allowedVersions) {

        if (ticket.getCreationDate() == null || ticket.getResolutionDate() == null) {
            return false;
        }
        if (ticket.getResolutionDate().isBefore(ticket.getCreationDate())) {
            return false;
        }
        if (versionRelation.getFixVersion() == null) {
            return false;
        }

        // la fix version deve essere tra le versioni ammesse
        String fixName = versionRelation.getFixVersion().getName();
        return allowedVersions.stream()
                .anyMatch(v -> v.getName().equals(fixName));
    }
}