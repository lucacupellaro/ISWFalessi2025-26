package service;

import domain.BugTicket;
import domain.ProjectVersion;
import domain.VersionRelation;

import java.util.List;

public class ConsistencyService {

    public boolean isValid(BugTicket ticket,
                           VersionRelation versionRelation,
                           List<ProjectVersion> allowedVersions) {
        return getDiscardReason(ticket, versionRelation, allowedVersions) == null;
    }

    public String getDiscardReason(BugTicket ticket,
                                   VersionRelation versionRelation,
                                   List<ProjectVersion> allowedVersions) {
        if (ticket.getCreationDate() == null) {
            return "creationDate mancante";
        }
        if (ticket.getResolutionDate() == null) {
            return "resolutionDate mancante";
        }
        if (ticket.getResolutionDate().isBefore(ticket.getCreationDate())) {
            return "resolutionDate precedente a creationDate";
        }
        if (versionRelation.getFixVersion() == null) {
            return "fixVersion mancante";
        }

        // la fix version deve avere una releaseDate valida
        ProjectVersion fix = versionRelation.getFixVersion();
        if (fix.getReleaseDate() == null) {
            return "fixVersion senza releaseDate";
        }

        // la releaseDate della fix version deve essere coerente con la creationDate
        // un ticket non può essere stato aperto DOPO la release in cui è stato fixato
        if (fix.getReleaseDate().isBefore(ticket.getCreationDate().toLocalDate())) {
            return "fixVersion '" + fix.getName() + "' rilasciata prima della creazione del ticket";
        }

        // la fix version deve essere tra le versioni ammesse
        String fixName = fix.getName();
        boolean fixInAllowed = allowedVersions.stream()
                .anyMatch(v -> v.getName().equals(fixName));
        if (!fixInAllowed) {
            return "fixVersion '" + fixName + "' non è nelle versioni ammesse";
        }

        return null;
    }
}