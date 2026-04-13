package org.example;

import client.GitHubCommitClient;
import client.JiraVersionClient;
import config.AppConfig;
import controller.AppController;
import controller.Phase2Controller;
import domain.BugTicketRecord;
import mapper.CommitMapper;
import service.ClassExtractorService;
import service.LabelingService;
import service.MetricsServices;
import service.VersionService;
import util.CsvTicketReader;
import util.PrintDatasetCsv;
import util.PrintOnCsv;
import util.ProgressLogger;

import java.io.File;
import java.util.List;

public class Main {

    private static final String CSV_PATH = "src/main/java/file/TicketRelease.csv";

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        int percent = config.getSettings().getMaxVersionsPercent();

        if (args.length == 0) {
            System.out.println("Specificare phase1 o phase2 come argomento");
            return;
        }

        if (args[0].equals("phase1")) {
            runPhase1(config, percent);
        } else if (args[0].equals("phase2")) {
            runPhase2(config);
        } else {
            System.out.println("Argomento non riconosciuto: " + args[0]);
        }
    }

    private static void runPhase1(AppConfig config, int percent) {
        if (new File(CSV_PATH).exists()) {
            System.out.println("TicketRelease.csv già presente, skip phase1");
            return;
        }

        System.out.println("Avvio phase1...");
        System.out.println("Si recuperano tutti i ticket del " + percent
                + "% delle release più vecchie");
        System.out.println("Progetti: " + config.getProjects().stream()
                .map(p -> p.getKey())
                .toList());

        String baseUrl = "https://issues.apache.org/jira";
        String username = "";
        String token = "";

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        PrintOnCsv csvPrinter = new PrintOnCsv();
        csvPrinter.print(records);

        System.out.println("Ticket validi recuperati: " + records.size());
    }

    private static void runPhase2(AppConfig config) throws Exception {
        if (!new File(CSV_PATH).exists()) {
            System.out.println("TicketRelease.csv non trovato — eseguire prima phase1");
            return;
        }

        System.out.println("Avvio phase2...");

        ProgressLogger logger = new ProgressLogger();
        String githubToken = config.getSettings().getGithubToken();

        GitHubCommitClient gitHubCommitClient = new GitHubCommitClient(githubToken, logger);
        JiraVersionClient jiraVersionClient = new JiraVersionClient(
                "https://issues.apache.org/jira", "", "");
        VersionService versionService = new VersionService(jiraVersionClient);
        CommitMapper commitMapper = new CommitMapper();
        ClassExtractorService classExtractorService = new ClassExtractorService(gitHubCommitClient, logger);
        MetricsServices metricsServices = new MetricsServices(gitHubCommitClient, logger);
        LabelingService labelingService = new LabelingService();
        PrintDatasetCsv printDatasetCsv = new PrintDatasetCsv();

        CsvTicketReader csvTicketReader = new CsvTicketReader();
        List<BugTicketRecord> tickets = csvTicketReader.read();

        Phase2Controller controller = new Phase2Controller(
                versionService, gitHubCommitClient, commitMapper,
                classExtractorService, metricsServices, labelingService,
                printDatasetCsv, logger);

        //paramtro preso da projects.yml
        int windowPercent = config.getSettings().getMaxVersionsPercent();

        config.getProjects().forEach(project -> {
            try {
                controller.run(project, tickets, windowPercent);
            } catch (Exception e) {
                logger.logWarning("Errore phase2 per progetto "
                        + project.getKey() + ": " + e.getMessage());
            }
        });
    }
}