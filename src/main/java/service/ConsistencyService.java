package service;

import domain.BugTicket;
import domain.ProjectVersion;
import domain.VersionRelation;

import java.time.LocalDate;
import java.util.List;

public class ConsistencyService {

    private static final String FIX_VERSION = "fixVersion";

    public String getDiscardReason(BugTicket ticket,
                                   VersionRelation versionRelation,
                                   List<ProjectVersion> allowedVersions) {
        String reason;

        reason = checkDates(ticket);
        if (reason != null) return reason;

        reason = checkFixVersion(ticket, versionRelation, allowedVersions);
        if (reason != null) return reason;

        reason = checkInjectionVersion(versionRelation);
        if (reason != null) return reason;

        reason = checkAffectedVersions(versionRelation);
        if (reason != null) return reason;

        return null;
    }

    private String checkDates(BugTicket ticket) {
        if (ticket.getCreationDate() == null) {
            return "creationDate mancante";
        }
        if (ticket.getResolutionDate() == null) {
            return "resolutionDate mancante";
        }
        if (ticket.getResolutionDate().isBefore(ticket.getCreationDate())) {
            return "resolutionDate precedente a creationDate";
        }
        return null;
    }

    private String checkFixVersion(BugTicket ticket,
                                   VersionRelation versionRelation,
                                   List<ProjectVersion> allowedVersions) {
        if (versionRelation.getFixVersion() == null) {
            return "fixVersion mancante";
        }

        // la fix version deve avere una releaseDate valida
        ProjectVersion fix = versionRelation.getFixVersion();
        if (fix.getReleaseDate() == null) {
            return FIX_VERSION + " senza releaseDate";
        }

        if (versionRelation.getAffectedVersions().stream()
                .anyMatch(v -> v.getName().equals(fix.getName()))) {
            return FIX_VERSION + " '" + fix.getName() + "' presente anche nelle affectedVersions";
        }

        if (versionRelation.getOpeningVersion() == null) {
            return "no opening version";
        }

        // la releaseDate della fix version deve essere coerente con la creationDate
        // un ticket non può essere stato aperto DOPO la release in cui è stato fixato
        if (fix.getReleaseDate().isBefore(ticket.getCreationDate().toLocalDate())) {
            return FIX_VERSION + " '" + fix.getName() + "' rilasciata prima della creazione del ticket";
        }

        // un ticket non può essere stato creato o chiuso prima della data di rilascio della prima versione
        if (!allowedVersions.isEmpty()) {
            LocalDate firstReleaseDate = allowedVersions.get(0).getReleaseDate();

            if (ticket.getCreationDate().toLocalDate().isBefore(firstReleaseDate)) {
                return "ticket creato prima della prima release (" + firstReleaseDate + ")";
            }

            if (ticket.getResolutionDate().toLocalDate().isBefore(firstReleaseDate)) {
                return "ticket chiuso prima della prima release (" + firstReleaseDate + ")";
            }
        }

        // la fix version deve essere tra le versioni ammesse, se non lo è viene scartata
        String fixName = fix.getName();
        boolean fixInAllowed = allowedVersions.stream()
                .anyMatch(v -> v.getName().equals(fixName));
        if (!fixInAllowed) {
            return FIX_VERSION + " '" + fixName + "' non è nelle versioni ammesse";
        }

        return null;
    }

    private String checkInjectionVersion(VersionRelation versionRelation) {
        // Il controllo verifica che la injection version sia stata rilasciata prima della fix version E non puo stare dopo la OV
        ProjectVersion iv = versionRelation.getInjectionVersion();
        ProjectVersion fix = versionRelation.getFixVersion();
        if (iv != null && iv.getReleaseDate() != null) {
            if (iv.getReleaseDate().isAfter(fix.getReleaseDate())) {
                return "injectionVersion '" + iv.getName()
                        + "' rilasciata dopo la fixVersion '" + fix.getName() + "'";
            }
            ProjectVersion ov = versionRelation.getOpeningVersion();
            if (ov != null && iv.getReleaseDate().isAfter(ov.getReleaseDate())) {
                return "injectionVersion '" + iv.getName()
                        + "' rilasciata dopo la openingVersion '" + ov.getName() + "'";
            }
        }
        return null;
    }

    private String checkAffectedVersions(VersionRelation versionRelation) {
        // le affectedVersions devono avere releaseDate compresa nell'intervallo [IV, FV)
        // se una affected ha data anomala (fuori intervallo) il ticket viene scartato
        ProjectVersion iv = versionRelation.getInjectionVersion();
        ProjectVersion fix = versionRelation.getFixVersion();
        if (iv != null && iv.getReleaseDate() != null) {
            for (ProjectVersion affected : versionRelation.getAffectedVersions()) {
                if (affected.getReleaseDate() == null) {
                    continue; // affected senza data, la saltiamo
                }
                boolean afterOrAtIv = !affected.getReleaseDate().isBefore(iv.getReleaseDate());
                boolean beforeFix = affected.getReleaseDate().isBefore(fix.getReleaseDate());
                if (!afterOrAtIv || !beforeFix) {
                    return "affectedVersion '" + affected.getName()
                            + "' con releaseDate " + affected.getReleaseDate()
                            + " fuori dall'intervallo [IV=" + iv.getName()
                            + ", FV=" + fix.getName() + ")";
                }
            }
        }
        return null;
    }
}