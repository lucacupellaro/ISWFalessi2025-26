package org.example;

import controller.AppController;
import domain.BugTicketRecord;

import java.util.List;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        String baseUrl = "https://issues.apache.org/jira";  // Jira pubblico Apache
        String username = "";
        String token = "";

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        System.out.println("Ticket validi recuperati: " + records.size());
        records.forEach(r -> System.out.println(
                r.getId()
                        + " | fix: " + (r.getFixVersion() != null ? r.getFixVersion().getName() : "n/a")
                        + " | affected: " + r.getAffectedVersions().size()
        ));
    }
}