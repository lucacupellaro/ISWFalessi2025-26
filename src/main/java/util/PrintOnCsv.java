package util;

import domain.BugTicketRecord;
import domain.ProjectVersion;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Scrive la lista di BugTicketRecord su un file CSV.
 * Il file viene riscritto completamente ad ogni chiamata.
 */
public class PrintOnCsv {

    private static final String FILE_NAME = "TicketRelease.csv";
    private static final String SEPARATOR = ",";
    private static final String HEADER = String.join(SEPARATOR,
            "projectKey",
            "idTicket",
            "creationDateTicket",
            "resolutionDateTicket",
            "fixVersion",
            "fixVersionReleaseDate",
            "affectedVersionsCount",
            "affectedVersions",
            "openingVersion",
            "openingVersionReleaseDate"
    );

    public void print(List<BugTicketRecord> records) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, false))) {
            writer.write(HEADER);
            writer.newLine();

            for (BugTicketRecord r : records) {
                writer.write(buildRow(r));
                writer.newLine();
            }

            System.out.println("[CSV] Scritti " + records.size() + " record su " + FILE_NAME);

        } catch (IOException e) {
            throw new IllegalStateException("Errore scrittura CSV: " + e.getMessage(), e);
        }
    }

    private String buildRow(BugTicketRecord r) {
        ProjectVersion ov = r.getVersionRelation() != null ? r.getVersionRelation().getOpeningVersion() : null;
        return String.join(SEPARATOR,
                safe(r.getProjectKey()),
                safe(r.getId()),
                safe(r.getCreationDate() != null ? r.getCreationDate().toString() : null),
                safe(r.getResolutionDate() != null ? r.getResolutionDate().toString() : null),

                safe(r.getFixVersion() != null ? r.getFixVersion().getName() : null),
                safe(r.getFixVersion() != null && r.getFixVersion().getReleaseDate() != null
                        ? r.getFixVersion().getReleaseDate().toString() : null),
                safe(String.valueOf(r.getAffectedVersions().size())),
                safe(r.getAffectedVersions().stream()
                        .map(ProjectVersion::getName)
                        .reduce((a, b) -> a + "|" + b)
                        .orElse("n/a")),
                safe(ov != null ? ov.getName() : null),
                safe(ov != null && ov.getReleaseDate() != null ? ov.getReleaseDate().toString() : null)
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