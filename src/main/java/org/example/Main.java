package org.example;

import config.AppConfig;
import controller.AppController;
import domain.BugTicketRecord;
import util.PrintOnCsv;

import java.io.InputStream;
import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        String baseUrl = "https://issues.apache.org/jira";  // Jira pubblico Apache
        String username = "";
        String token = "";

        //recupero informazione dal file project che vine mappato tramite json nella classe AppConfig
        AppConfig config = AppConfig.load();
        int percent = config.getSettings().getMaxVersionsPercent();


        System.out.println("Si recuperano tutti i ticket del " + percent + "% delle release più vecchie");
        System.out.println("Progetti: " + config.getProjects().stream()
                .map(p -> p.getKey())
                .toList());

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        //Stampa on csv i ticket con relase
        PrintOnCsv csvPrinter = new PrintOnCsv();
        csvPrinter.print(records);



        System.out.println("Ticket validi recuperati: " + records.size());
        records.forEach(r -> System.out.println(
                "progetto: " + r.getProjectKey()
                        + " | id Ticket: " + r.getId()
                        + " | release associata: " + (r.getAssociatedRelease() != null ? r.getAssociatedRelease().getName() : "n/a")
                        + " | release date: " + (r.getAssociatedRelease() != null && r.getAssociatedRelease().getReleaseDate() != null ? r.getAssociatedRelease().getReleaseDate() : "n/a")
                        + " | creazione Ticket: " + (r.getCreationDate() != null ? r.getCreationDate() : "n/a")
                        + " | risoluzione Ticket: " + (r.getResolutionDate() != null ? r.getResolutionDate() : "n/a")
                        + " | fix: " + (r.getFixVersion() != null ? r.getFixVersion().getName() : "n/a")
                        + " | fix release date: " + (r.getFixVersion() != null && r.getFixVersion().getReleaseDate() != null ? r.getFixVersion().getReleaseDate() : "n/a")
                        + " | affected count: " + r.getAffectedVersions().size()
                        + " | affected: " + r.getAffectedVersions().stream().map(v -> v.getName()).toList()
        ));
    }
}