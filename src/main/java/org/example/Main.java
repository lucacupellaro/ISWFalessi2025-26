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
import java.io.IOException;
import java.util.List;
import java.util.logging.*;

public class Main {

    private static final Logger logger = Logger.getLogger(Main.class.getName());
    private static final String CSV_PATH = "src/main/java/file/TicketRelease.csv";

    public static void main(String[] args) throws IOException, InterruptedException {
        configureLogging();
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
            runPhase3();
        } else {
            logger.log(Level.INFO, "Argomento non riconosciuto: {0}", args[0]);
        }
    }

    private static void runPhase1(AppConfig config, int percent) {
        if (new File(CSV_PATH).exists()) {
            logger.info("TicketRelease.csv già presente, skip phase1");
            return;
        }

        logger.log(Level.FINE, "Avvio phase1 — {0}% release, progetti: {1}",
                new Object[]{percent, config.getProjects().stream().map(p -> p.getKey()).toList()});

        String baseUrl = "https://issues.apache.org/jira";
        String username = "";
        String token = "";

        AppController controller = new AppController(baseUrl, username, token);
        List<BugTicketRecord> records = controller.run();

        PrintOnCsv csvPrinter = new PrintOnCsv();
        csvPrinter.print(records);

        logger.log(Level.INFO, "Ticket validi recuperati: {0}", records.size());
    }

    private static void runPhase2(AppConfig config) throws IOException, InterruptedException {
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
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.logWarning("Errore phase2 per progetto "
                        + project.getKey() + ": " + e.getMessage());
            } catch (Exception e) {
                logger.logWarning("Errore phase2 per progetto "
                        + project.getKey() + ": " + e.getMessage());
            }
        });
    }

    private static void runPhase3() throws IOException {
        logger.info("Avvio phase3 — MLWeka Pipeline...");
        MLWekaController controller = new MLWekaController();
        controller.normalizeDataset();
    }

    private static void configureLogging() {
        Logger rootLogger = Logger.getLogger("");
        for (Handler h : rootLogger.getHandlers()) {
            rootLogger.removeHandler(h);
        }
        StreamHandler stdoutHandler = new StreamHandler(System.out, new SimpleFormatter()) {
            @Override
            public synchronized void publish(LogRecord record) {
                super.publish(record);
                flush();
            }
        };
        stdoutHandler.setLevel(Level.ALL);
        rootLogger.addHandler(stdoutHandler);
    }
}