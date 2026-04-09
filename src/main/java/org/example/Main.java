package org.example;

import config.AppConfig;
import controller.AppController;
import domain.BugTicketRecord;
import util.PrintOnCsv;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        String baseUrl = "https://issues.apache.org/jira";
        String username = "";
        String token = "";

        AppConfig config = AppConfig.load();
        int percent = config.getSettings().getMaxVersionsPercent();

        System.out.println("Si recuperano tutti i ticket del " + percent + "% delle release più vecchie");
        System.out.println("Progetti: " + config.getProjects().stream()
                .map(p -> p.getKey())
                .toList());

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        PrintOnCsv csvPrinter = new PrintOnCsv();
        csvPrinter.print(records);

        System.out.println("Ticket validi recuperati: " + records.size());
        records.forEach(r -> System.out.println(
                "progetto: " + r.getProjectKey()
                        + " | id: " + r.getId()
                        + " | creazione: " + (r.getCreationDate() != null ? r.getCreationDate() : "n/a")
                        + " | risoluzione: " + (r.getResolutionDate() != null ? r.getResolutionDate() : "n/a")
                        + " | fix: " + (r.getFixVersionName() != null ? r.getFixVersionName() : "n/a")
                        + " | fix release: " + (r.getFixVersionReleaseDate() != null ? r.getFixVersionReleaseDate() : "n/a")
                        + " | affected count: " + r.getAffectedVersionsCount()
                        + " | affected: " + r.getAffectedVersionNames()
        ));
    }
}
