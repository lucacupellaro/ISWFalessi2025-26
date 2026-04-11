package util;


import domain.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CsvTicketReader {

    private static final String CSV_PATH = "file/TicketRelease.csv";

    public List<BugTicketRecord> read() throws IOException {
        List<BugTicketRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(CSV_PATH))) {
            String header = reader.readLine();
            if (header == null) {
                return records;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                BugTicketRecord record = parseLine(line);
                if (record != null) {
                    records.add(record);
                }
            }
        }

        return records;
    }

    private BugTicketRecord parseLine(String line) {
        String[] fields = line.split(",", -1);
        if (fields.length < 13) {
            return null;
        }

        String projectKey        = fields[0].trim();
        String ticketId          = fields[1].trim();
        String ticketKey         = fields[2].trim();
        LocalDateTime creation   = parseDateTime(fields[3].trim());
        LocalDateTime resolution = parseDateTime(fields[4].trim());

        ProjectVersion fixVersion       = parseVersion(fields[5].trim(), fields[6].trim());
        ProjectVersion openingVersion   = parseVersion(fields[9].trim(), fields[10].trim());
        ProjectVersion injectionVersion = parseVersion(fields[11].trim(), fields[12].trim());

        List<ProjectVersion> affectedVersions = parseAffectedVersions(fields[8].trim());

        BugTicket ticket = new BugTicket(ticketId, ticketKey, creation, resolution);
        VersionRelation versionRelation = new VersionRelation(
                fixVersion, affectedVersions, openingVersion, injectionVersion);

        return new BugTicketRecord(ticket, versionRelation, projectKey);
    }

    private ProjectVersion parseVersion(String name, String dateStr) {
        if (name.isEmpty()) {
            return null;
        }
        LocalDate date = parseDate(dateStr);
        return new ProjectVersion(name, date);
    }

    private List<ProjectVersion> parseAffectedVersions(String raw) {
        if (raw.isEmpty() || raw.equals("[]")) {
            return new ArrayList<>();
        }
        String cleaned = raw.replace("[", "").replace("]", "");
        return Arrays.stream(cleaned.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(name -> new ProjectVersion(name, null))
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDateTime(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            return null;
        }
    }
}