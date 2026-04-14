package controller;

import client.GitHubCommitClient;
import config.ProjectConfig;
import domain.*;
import mapper.CommitMapper;
import service.*;
import util.ProgressLogger;
import util.PrintDatasetCsv;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Phase2Controller {

    private final VersionService versionService;
    private final GitHubCommitClient gitHubCommitClient;
    private final CommitMapper commitMapper;
    private final ClassExtractorService classExtractorService;
    private final MetricsServices metricsServices;
    private final LabelingService labelingService;
    private final PrintDatasetCsv printDatasetCsv;
    private final ProgressLogger logger;

    public Phase2Controller(VersionService versionService,
                            GitHubCommitClient gitHubCommitClient,
                            CommitMapper commitMapper,
                            ClassExtractorService classExtractorService,
                            MetricsServices metricsServices,
                            LabelingService labelingService,
                            PrintDatasetCsv printDatasetCsv,
                            ProgressLogger logger) {
        this.versionService = versionService;
        this.gitHubCommitClient = gitHubCommitClient;
        this.commitMapper = commitMapper;
        this.classExtractorService = classExtractorService;
        this.metricsServices = metricsServices;
        this.labelingService = labelingService;
        this.printDatasetCsv = printDatasetCsv;
        this.logger = logger;
    }

    public void run(ProjectConfig projectConfig,
                    List<BugTicketRecord> tickets,
                    int windowPercent)
            throws IOException, InterruptedException {

        String projectKey = projectConfig.getKey();
        String repoName = projectConfig.getRepoName();

        logger.logInfo("Scarico release reali da Jira per " + projectKey + " e applico il taglio al .." + windowPercent + "%");
        List<ProjectVersion> windowedReleases = versionService.loadVersions(projectKey, windowPercent);
        logger.logInfo("Release nella finestra: " + windowedReleases.size());

        logger.logInfo("Scarico commit da GitHub per " + repoName + "...");
        List<GitCommit> commits = commitMapper.mapCommits(
                gitHubCommitClient.fetchAllCommits(projectConfig));
        logger.logInfo("Totale commit scaricati: " + commits.size());

        // ogni commit viene assegnato alla sua release
        for (GitCommit commit : commits) {
            ProjectVersion release = assignCommitToRelease(windowedReleases, commit.getDate());
            commit.setRelease(release);
        }

        // === CONSISTENCY CHECK: commit per release ===
        long commitsConRelease = commits.stream().filter(c -> c.getRelease() != null).count();
        long commitsSenzaRelease = commits.stream().filter(c -> c.getRelease() == null).count();
        logger.logInfo("[CONSISTENCY] Commit assegnati a una release: " + commitsConRelease + "/" + commits.size());
        if (commitsSenzaRelease > 0) {
            logger.logInfo("[CONSISTENCY] Commit senza release (fuori finestra): " + commitsSenzaRelease);
        }

        logger.logInfo("Scarico touched paths e diffs per ogni commit...");
        for (GitCommit commit : commits) {
            if (commit.getTouchedPaths() == null) {
                gitHubCommitClient.fetchTouchedPathsAndDiffs(repoName, commit);
            }
        }
        logger.logInfo("Touched paths scaricati.");

        logger.logInfo("Estrazione classi e calcolo metriche per release...");
        List<JavaClass> allClasses = new ArrayList<>();
        for (ProjectVersion release : windowedReleases) {
            List<JavaClass> classes = classExtractorService
                    .extractClassesForRelease(release, commits, repoName);

            metricsServices.computeAllMetrics(classes, commits, repoName);

            // === CONSISTENCY CHECK: classi per release ===
            logger.logInfo("[CONSISTENCY] Release " + release.getName()
                    + " -> classi estratte: " + classes.size());

            allClasses.addAll(classes);
        }
        logger.logInfo("Totale classi estratte: " + allClasses.size());

        // === CONSISTENCY CHECK: classi uniche per nome ===
        long classiUniche = allClasses.stream()
                .map(JavaClass::getName)
                .distinct()
                .count();
        logger.logInfo("[CONSISTENCY] Nomi classe distinti (su tutte le release): " + classiUniche);

        logger.logInfo("Avvio labeling...");
        List<BugTicketRecord> projectTickets = tickets.stream()
                .filter(t -> t.getProjectKey().equals(projectKey))
                .toList();

        // === CONSISTENCY CHECK: ticket disponibili per il labeling ===
        long ticketsConIV = projectTickets.stream()
                .filter(t -> t.getInjectionVersion() != null)
                .count();
        long ticketsSenzaIV = projectTickets.stream()
                .filter(t -> t.getInjectionVersion() == null)
                .count();
        logger.logInfo("[CONSISTENCY] Ticket del progetto: " + projectTickets.size()
                + " | con IV: " + ticketsConIV
                + " | senza IV (saltati): " + ticketsSenzaIV);

        int ticketsConCommit = 0;
        int ticketsSenzaCommit = 0;

        for (BugTicketRecord ticket : projectTickets) {

            if (ticket.getInjectionVersion() == null) {
                logger.logWarning("Ticket " + ticket.getId() + " senza IV → saltato");
                continue;
            }

            List<GitCommit> relevantCommits = commits.stream()
                    .filter(c -> c.getMessage().contains(ticket.getTicketKey()))
                    .toList();

            if (relevantCommits.isEmpty()) {
                ticketsSenzaCommit++;
                logger.logWarning("Nessun commit trovato per ticket " + ticket.getTicketKey());
                continue;
            }

            ticketsConCommit++;

            for (GitCommit commit : relevantCommits) {
                if (commit.getTouchedPaths() == null) {
                    gitHubCommitClient.fetchTouchedPathsAndDiffs(repoName, commit);
                }
            }

            labelingService.assignLabels(
                    allClasses,
                    relevantCommits,
                    windowedReleases,
                    ticket.getInjectionVersion(),
                    ticket.getVersionRelation().getFixVersion());
        }

        // === CONSISTENCY CHECK: risultato labeling ===
        logger.logInfo("[CONSISTENCY] Ticket con almeno un commit: " + ticketsConCommit
                + " | senza commit (non labellati): " + ticketsSenzaCommit);

        long classiBuggy = allClasses.stream().filter(JavaClass::isBuggy).count();
        long classiClean = allClasses.stream().filter(c -> !c.isBuggy()).count();
        logger.logInfo("[CONSISTENCY] Classi buggy: " + classiBuggy
                + " | classi clean: " + classiClean
                + " | totale: " + allClasses.size());

        logger.logInfo("Labeling completato per " + projectKey);

        logger.logInfo("Scrittura dataset...");
        List<ClassRecord> records = allClasses.stream()
                .map(c -> new ClassRecord(
                        c.getRelease(),
                        c.getName(),
                        c.getLoc(),
                        c.getCommentLines(),
                        c.getNRevisions(),
                        c.getNAuth(),
                        c.getNFix(),
                        c.getLocAdded(),
                        c.getMaxLocAdded(),
                        c.getAvgLocAdded(),
                        c.getChurn(),
                        c.getMaxChurn(),
                        c.getAvgChurn(),
                        c.getLocTouched(),
                        c.isBuggy()))
                .toList();

        printDatasetCsv.write(records);

        // === CONSISTENCY CHECK FINALE ===
        logger.logInfo("[CONSISTENCY] ============ RIEPILOGO FASE 2 ============");
        logger.logInfo("[CONSISTENCY] Progetto         : " + projectKey);
        logger.logInfo("[CONSISTENCY] Release analizzate: " + windowedReleases.size());
        logger.logInfo("[CONSISTENCY] Commit totali     : " + commits.size()
                + " (assegnati: " + commitsConRelease + ", fuori finestra: " + commitsSenzaRelease + ")");
        logger.logInfo("[CONSISTENCY] Classi totali     : " + allClasses.size()
                + " (nomi distinti: " + classiUniche + ")");
        logger.logInfo("[CONSISTENCY] Classi buggy      : " + classiBuggy
                + " (" + String.format("%.1f", 100.0 * classiBuggy / allClasses.size()) + "%)");
        logger.logInfo("[CONSISTENCY] Ticket usati      : " + ticketsConCommit
                + "/" + ticketsConIV + " con IV");
        logger.logInfo("[CONSISTENCY] Record CSV scritti: " + records.size());
        logger.logInfo("[CONSISTENCY] ==========================================");
    }


    // assegno il commit alla sua release in base alla data
    private ProjectVersion assignCommitToRelease(List<ProjectVersion> releases,
                                                 LocalDate commitDate) {
        for (int i = 0; i < releases.size(); i++) {
            ProjectVersion current = releases.get(i);
            boolean isLast = (i == releases.size() - 1);
            ProjectVersion next = isLast ? null : releases.get(i + 1);

            boolean afterCurrent = !commitDate.isBefore(current.getReleaseDate());
            boolean beforeNext = next == null || commitDate.isBefore(next.getReleaseDate());

            if (afterCurrent && beforeNext) {
                return current;
            }
        }
        return null;
    }
}