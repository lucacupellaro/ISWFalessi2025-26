package util;

import domain.BugTicketRecord;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Scrive la lista di BugTicketRecord su un file CSV.
 * Dipende esclusivamente da BugTicketRecord — nessuna conoscenza
 * delle classi di dominio interne (ProjectVersion, VersionRelation).
 * Il file viene riscritto completamente ad ogni chiamata.
 */
public class PrintOnCsv {

    private static final Logger logger = Logger.getLogger(PrintOnCsv.class.getName());
    private static final String FILE_NAME = "src/main/java/file/TicketRelease.csv";
    private static final String SEPARATOR = ",";
    private static final String HEADER = String.join(SEPARATOR,
            "projectKey",
            "idTicket",
            "ticketKey",
            "creationDateTicket",
            "resolutionDateTicket",
            "fixVersion",
            "fixVersionReleaseDate",
            "affectedVersionsCount",
            "affectedVersions",
            "openingVersion",
            "openingVersionReleaseDate",
            "injectionVersion",
            "injectionVersionReleaseDate"
    );

    public void print(List<BugTicketRecord> records) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, false))) {
            writer.write(HEADER);
            writer.newLine();

            for (BugTicketRecord r : records) {
                writer.write(buildRow(r));
                writer.newLine();
            }

            logger.log(Level.INFO, "[CSV] Scritti {0} record su {1}",
                    new Object[]{records.size(), FILE_NAME});

        } catch (IOException e) {
            throw new IllegalStateException("Errore scrittura CSV: " + e.getMessage(), e);
        }
    }

    private String buildRow(BugTicketRecord r) {
        String affectedJoined = r.getAffectedVersionNames().isEmpty()
                ? "n/a"
                : String.join("|", r.getAffectedVersionNames());

        return String.join(SEPARATOR,
                safe(r.getProjectKey()),
                safe(r.getId()),
                safe(r.getTicketKey()),
                safe(r.getCreationDate() != null ? r.getCreationDate().toString() : null),
                safe(r.getResolutionDate() != null ? r.getResolutionDate().toString() : null),
                safe(r.getFixVersionName()),
                safe(r.getFixVersionReleaseDate()),
                safe(String.valueOf(r.getAffectedVersionsCount())),
                safe(affectedJoined),
                safe(r.getOpeningVersionName()),
                safe(r.getOpeningVersionReleaseDate()),
                safe(r.getInjectionVersionName()),
                safe(r.getInjectionVersionReleaseDate())
        );
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "n/a";
        }
        if (value.contains(SEPARATOR) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
