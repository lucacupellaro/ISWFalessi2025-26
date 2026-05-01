package org.example;

import client.JiraVersionClient;
import config.AppConfig;
import controller.AppController;
import controller.MLWekaController;
import controller.Phase2Controller;
import domain.BugTicketRecord;
import service.ClassExtractorService;
import service.LabelingService;
import service.LocalGitService;
import service.MetricsServices;
import service.VersionService;
import util.CsvTicketReader;
import util.PrintDatasetCsv;
import util.PrintOnCsv;
import util.ProgressLogger;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final String CSV_PATH = "src/main/java/file/TicketRelease.csv";

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load();
        int percent = config.getSettings().getMaxVersionsPercent();

        if (args.length == 0) {
            logger.info("Specificare phase1, phase2 o phase3 come argomento");
            return;
        }

        if (args[0].equals("phase1")) {
            runPhase1(config, percent);
        } else if (args[0].equals("phase2")) {
            runPhase2(config);
        } else if (args[0].equals("phase3")) {
            runPhase3(args);
        } else {
            logger.info("Argomento non riconosciuto: " + args[0]);
        }
    }

    private static void runPhase1(AppConfig config, int percent) {
        if (new File(CSV_PATH).exists()) {
            logger.info("TicketRelease.csv già presente, skip phase1");
            return;
        }

        logger.info("Avvio phase1...");
        logger.info("Si recuperano tutti i ticket del " + percent
                + "% delle release più vecchie");
        logger.info("Progetti: " + config.getProjects().stream()
                .map(p -> p.getKey())
                .toList());

        String baseUrl = "https://issues.apache.org/jira";
        String username = "";
        String token = "";

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        PrintOnCsv csvPrinter = new PrintOnCsv();
        csvPrinter.print(records);

        logger.info("Ticket validi recuperati: " + records.size());
    }

    private static void runPhase2(AppConfig config) throws Exception {
        if (!new File(CSV_PATH).exists()) {
            logger.info("TicketRelease.csv non trovato — eseguire prima phase1");
            return;
        }

        logger.info("Avvio phase2...");

        ProgressLogger logger = new ProgressLogger();

        JiraVersionClient jiraVersionClient = new JiraVersionClient(
                "https://issues.apache.org/jira", "", "");
        VersionService versionService = new VersionService(jiraVersionClient);

        // Clone locale del repo — un solo download, poi tutto offline
        String repoName = config.getProjects().get(0).getRepoName();
        LocalGitService localGitService = new LocalGitService(repoName, logger);
        ClassExtractorService classExtractorService = new ClassExtractorService(localGitService, logger);
        MetricsServices metricsServices = new MetricsServices(logger, localGitService);
        LabelingService labelingService = new LabelingService();
        PrintDatasetCsv printDatasetCsv = new PrintDatasetCsv();

        CsvTicketReader csvTicketReader = new CsvTicketReader();
        List<BugTicketRecord> tickets = csvTicketReader.read();

        Phase2Controller controller = new Phase2Controller(
                versionService, localGitService,
                classExtractorService, metricsServices, labelingService,
                printDatasetCsv, logger);

        //parametro preso da projects.yml
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

    private static void runPhase3(String[] args) throws Exception {

        logger.info("Avvio phase3 — MLWeka Pipeline...");
        MLWekaController controller = new MLWekaController();

       // controller.normalizeDataset()
        controller.normalizeDataset();
        /*
        if (args[1].equals("FeatureS")) {
            controller.runFeatureSelection();
        } else if (args[1].equals("Balancing")) {
            controller.runBalancing();
        }
        */



    }
}