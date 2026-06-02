package controller;

import config.ProjectConfig;
import domain.*;
import service.*;
import util.ProgressLogger;
import util.PrintDatasetCsv;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Phase2Controller {

    private final VersionService versionService;
    private final LocalGitService localGitService;
    private final ClassExtractorService classExtractorService;
    private final MetricsServices metricsServices;
    private final LabelingService labelingService;
    private final PrintDatasetCsv printDatasetCsv;
    private final ProgressLogger logger;

    public Phase2Controller(VersionService versionService,
                            LocalGitService localGitService,
                            ClassExtractorService classExtractorService,
                            MetricsServices metricsServices,
                            LabelingService labelingService,
                            PrintDatasetCsv printDatasetCsv,
                            ProgressLogger logger) {
        this.versionService = versionService;
        this.localGitService = localGitService;
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

        logger.logInfo("Scarico release reali da Jira per " + projectKey + " e applico il taglio al " + windowPercent + "%");
        List<ProjectVersion> windowedReleases = versionService.loadVersions(projectKey, windowPercent);

        if (windowedReleases == null || windowedReleases.isEmpty()) {
            logger.logWarning("Nessuna release disponibile nella finestra per il progetto " + projectKey);
            return;
        }

        logger.logInfo("Release nella finestra: " + windowedReleases.size());

        LocalDate windowStart = windowedReleases.get(0).getReleaseDate();
        LocalDate windowEnd = windowedReleases.get(windowedReleases.size() - 1).getReleaseDate();

        logger.logInfo("Finestra temporale considerata: [" + windowStart + " -> " + windowEnd + "]");

        List<GitCommit> allCommits = loadAndFilterCommits(repoName, windowedReleases, windowStart, windowEnd);
        List<GitCommit> commits = allCommits.stream()
                .filter(c -> c.getDate() != null)
                .filter(c -> !c.getDate().isBefore(windowStart))
                .filter(c -> !c.getDate().isAfter(windowEnd))
                .toList();

        List<JavaClass> allClasses = extractAndComputeMetrics(windowedReleases, commits, allCommits);

        int[] labelingCounts = labelClasses(projectKey, tickets, commits, allClasses, windowedReleases);
        int ticketsConCommit = labelingCounts[0];

        long ticketsConIV = tickets.stream()
                .filter(t -> t.getProjectKey().equals(projectKey))
                .filter(t -> t.getInjectionVersion() != null)
                .count();

        long classiBuggy = allClasses.stream().filter(JavaClass::isBuggy).count();

        logger.logInfo("Scrittura dataset...");
        List<ClassRecord> records = buildRecords(allClasses);
        printDatasetCsv.write(records);

        LogSummaryParams summaryParams = new LogSummaryParams()
                .setProjectKey(projectKey)
                .setWindowedReleases(windowedReleases)
                .setWindowStart(windowStart)
                .setWindowEnd(windowEnd)
                .setAllCommits(allCommits)
                .setCommits(commits)
                .setAllClasses(allClasses)
                .setClassiBuggy(classiBuggy)
                .setTicketsConCommit(ticketsConCommit)
                .setTicketsConIV(ticketsConIV)
                .setRecords(records);
        logSummary(summaryParams);
    }

    private List<GitCommit> loadAndFilterCommits(String repoName,
                                                  List<ProjectVersion> windowedReleases,
                                                  LocalDate windowStart,
                                                  LocalDate windowEnd)
            throws IOException, InterruptedException {

        logger.logInfo("Leggo commit dal repo locale per " + repoName + "...");
        List<GitCommit> allCommits = localGitService.fetchAllCommits();
        logger.logInfo("Totale commit letti: " + allCommits.size());

        // Mantengo solo i commit compresi nella finestra temporale delle release considerate
        List<GitCommit> commits = allCommits.stream()
                .filter(c -> c.getDate() != null)
                .filter(c -> !c.getDate().isBefore(windowStart))
                .filter(c -> !c.getDate().isAfter(windowEnd))
                .toList();

        long commitsFuoriFinestra = allCommits.size() - commits.size();

        logger.logInfo("[CONSISTENCY] Commit nella finestra: " + commits.size() + "/" + allCommits.size());
        logger.logInfo("[CONSISTENCY] Commit fuori finestra: " + commitsFuoriFinestra);

        // Ogni commit nella finestra viene assegnato alla release opportuna
        for (GitCommit commit : commits) {
            ProjectVersion release = assignCommitToRelease(windowedReleases, commit.getDate());
            commit.setRelease(release);
        }

        long commitsConRelease = commits.stream().filter(c -> c.getRelease() != null).count();
        long commitsSenzaRelease = commits.stream().filter(c -> c.getRelease() == null).count();

        logger.logInfo("[CONSISTENCY] Commit nella finestra assegnati a una release: "
                + commitsConRelease + "/" + commits.size());

        if (commitsSenzaRelease > 0) {
            logger.logWarning("[CONSISTENCY] Commit nella finestra ma senza release assegnata: " + commitsSenzaRelease);
        }

        return allCommits;
    }

    private List<JavaClass> extractAndComputeMetrics(List<ProjectVersion> windowedReleases,
                                                      List<GitCommit> commits,
                                                      List<GitCommit> allCommits)
            throws IOException, InterruptedException {

        logger.logInfo("Estrazione classi e calcolo metriche per release...");
        List<JavaClass> allClasses = new ArrayList<>();

        for (int i = 0; i < windowedReleases.size(); i++) {
            ProjectVersion release = windowedReleases.get(i);

            List<JavaClass> classes = classExtractorService
                    .extractClassesForRelease(release, commits);

            // Trova lo SHA dell'ultimo commit della release precedente (null per la prima)
            String previousReleaseSha = null;
            if (i > 0) {
                ProjectVersion prevRelease = windowedReleases.get(i - 1);
                previousReleaseSha = findLastCommitSha(allCommits, prevRelease);
            }

            metricsServices.computeAllMetrics(classes, commits, release, previousReleaseSha);

            logger.logInfo("[CONSISTENCY] Release " + release.getName()
                    + " -> classi estratte: " + classes.size());

            allClasses.addAll(classes);
        }

        logger.logInfo("Totale classi estratte: " + allClasses.size());

        long classiUniche = allClasses.stream()
                .map(JavaClass::getName)
                .distinct()
                .count();

        logger.logInfo("[CONSISTENCY] Nomi classe distinti (su tutte le release): " + classiUniche);

        return allClasses;
    }

    private int[] labelClasses(String projectKey,
                               List<BugTicketRecord> tickets,
                               List<GitCommit> commits,
                               List<JavaClass> allClasses,
                               List<ProjectVersion> windowedReleases) {

        logger.logInfo("Avvio labeling...");
        List<BugTicketRecord> projectTickets = tickets.stream()
                .filter(t -> t.getProjectKey().equals(projectKey))
                .toList();

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

        Map<String, LocalDate> releaseNameToDate = windowedReleases.stream()
                .collect(Collectors.toMap(ProjectVersion::getName, ProjectVersion::getReleaseDate));

        for (BugTicketRecord ticket : projectTickets) {
            int result = processTicketForLabeling(ticket, commits, allClasses, windowedReleases, releaseNameToDate);
            if (result > 0) {
                ticketsConCommit++;
            } else if (result < 0) {
                ticketsSenzaCommit++;
            }
        }

        logger.logInfo("[CONSISTENCY] Ticket con almeno un commit nella finestra: "
                + ticketsConCommit + " | senza commit: " + ticketsSenzaCommit);

        long classiBuggy = allClasses.stream().filter(JavaClass::isBuggy).count();
        long classiClean = allClasses.stream().filter(c -> !c.isBuggy()).count();

        logger.logInfo("[CONSISTENCY] Classi buggy: " + classiBuggy
                + " | classi clean: " + classiClean
                + " | totale: " + allClasses.size());

        logger.logInfo("Labeling completato per " + projectKey);

        return new int[]{ticketsConCommit, ticketsSenzaCommit};
    }

    /**
     * Processes a single ticket for labeling.
     * Returns: +1 if the ticket had relevant commits, -1 if no commits found, 0 if skipped (no IV).
     */
    private int processTicketForLabeling(BugTicketRecord ticket,
                                          List<GitCommit> commits,
                                          List<JavaClass> allClasses,
                                          List<ProjectVersion> windowedReleases,
                                          Map<String, LocalDate> releaseNameToDate) {

        if (ticket.getInjectionVersion() == null) {
            logger.logWarning("Ticket " + ticket.getId() + " senza IV -> saltato");
            return 0;
        }

        List<GitCommit> relevantCommits = commits.stream()
                .filter(c -> c.getMessage() != null && c.getMessage().contains(ticket.getTicketKey()))
                .toList();

        if (relevantCommits.isEmpty()) {
            logger.logWarning("Nessun commit trovato nella finestra per ticket " + ticket.getTicketKey());
            return -1;
        }

        labelingService.assignLabels(
                allClasses,
                relevantCommits,
                windowedReleases,
                ticket.getInjectionVersion(),
                ticket.getVersionRelation().getFixVersion());

        // nFix: per ogni classe toccata da commit di bug-fix, incrementa solo
        // le classi la cui release ha data >= data del commit (cumulativo come nRevisions)
        for (GitCommit commit : relevantCommits) {
            if (commit.getTouchedPaths() == null || commit.getDate() == null) {
                continue;
            }

            for (String path : commit.getTouchedPaths()) {
                allClasses.stream()
                        .filter(c -> c.getPath().equals(path))
                        .filter(c -> {
                            LocalDate relDate = releaseNameToDate.get(c.getRelease());
                            return relDate != null && !relDate.isBefore(commit.getDate());
                        })
                        .forEach(c -> c.setNFix(c.getNFix() + 1));
            }
        }

        return 1;
    }

    private List<ClassRecord> buildRecords(List<JavaClass> allClasses) {
        return allClasses.stream()
                .map(c -> new ClassRecord.Builder()
                        .setRelease(c.getRelease())
                        .setClassName(c.getPath())
                        .setLoc(c.getLoc())
                        .setCommentLines(c.getCommentLines())
                        .setNRevisions(c.getNRevisions())
                        .setNAuth(c.getNAuth())
                        .setNFix(c.getNFix())
                        .setLocAdded(c.getLocAdded())
                        .setMaxLocAdded(c.getMaxLocAdded())
                        .setAvgLocAdded(c.getAvgLocAdded())
                        .setChurn(c.getChurn())
                        .setMaxChurn(c.getMaxChurn())
                        .setAvgChurn(c.getAvgChurn())
                        .setLocTouched(c.getLocTouched())
                        .setChangeSetSize(c.getChangeSetSize())
                        .setMaxChangeSet(c.getMaxChangeSet())
                        .setAvgChangeSet(c.getAvgChangeSet())
                        .setAge(c.getAge())
                        .setWeightedAge(c.getWeightedAge())
                        .setCyclomaticComplexity(c.getCyclomaticComplexity())
                        .setDuplication(c.getDuplication())
                        .setNBranches(c.getNBranches())
                        .setMaxNestingDepth(c.getMaxNestingDepth())
                        .setBuggy(c.isBuggy())
                        .setNSmells(c.getNSmells())
                        .build())
                .toList();
    }

    private static class LogSummaryParams {
        private String projectKey;
        private List<ProjectVersion> windowedReleases;
        private LocalDate windowStart;
        private LocalDate windowEnd;
        private List<GitCommit> allCommits;
        private List<GitCommit> commits;
        private List<JavaClass> allClasses;
        private long classiBuggy;
        private int ticketsConCommit;
        private long ticketsConIV;
        private List<ClassRecord> records;

        private LogSummaryParams setProjectKey(String projectKey) { this.projectKey = projectKey; return this; }
        private LogSummaryParams setWindowedReleases(List<ProjectVersion> windowedReleases) { this.windowedReleases = windowedReleases; return this; }
        private LogSummaryParams setWindowStart(LocalDate windowStart) { this.windowStart = windowStart; return this; }
        private LogSummaryParams setWindowEnd(LocalDate windowEnd) { this.windowEnd = windowEnd; return this; }
        private LogSummaryParams setAllCommits(List<GitCommit> allCommits) { this.allCommits = allCommits; return this; }
        private LogSummaryParams setCommits(List<GitCommit> commits) { this.commits = commits; return this; }
        private LogSummaryParams setAllClasses(List<JavaClass> allClasses) { this.allClasses = allClasses; return this; }
        private LogSummaryParams setClassiBuggy(long classiBuggy) { this.classiBuggy = classiBuggy; return this; }
        private LogSummaryParams setTicketsConCommit(int ticketsConCommit) { this.ticketsConCommit = ticketsConCommit; return this; }
        private LogSummaryParams setTicketsConIV(long ticketsConIV) { this.ticketsConIV = ticketsConIV; return this; }
        private LogSummaryParams setRecords(List<ClassRecord> records) { this.records = records; return this; }
    }

    private void logSummary(LogSummaryParams params) {

        long commitsFuoriFinestra = params.allCommits.size() - params.commits.size();
        long commitsConRelease = params.commits.stream().filter(c -> c.getRelease() != null).count();
        long commitsSenzaRelease = params.commits.stream().filter(c -> c.getRelease() == null).count();
        long classiUniche = params.allClasses.stream()
                .map(JavaClass::getName)
                .distinct()
                .count();

        logger.logInfo("[CONSISTENCY] ============ RIEPILOGO FASE 2 ============");
        logger.logInfo("[CONSISTENCY] Progetto              : " + params.projectKey);
        logger.logInfo("[CONSISTENCY] Release analizzate    : " + params.windowedReleases.size());
        logger.logInfo("[CONSISTENCY] Finestra temporale    : [" + params.windowStart + " -> " + params.windowEnd + "]");
        logger.logInfo("[CONSISTENCY] Commit totali scaricati: " + params.allCommits.size());
        logger.logInfo("[CONSISTENCY] Commit nella finestra : " + params.commits.size());
        logger.logInfo("[CONSISTENCY] Commit fuori finestra : " + commitsFuoriFinestra);
        logger.logInfo("[CONSISTENCY] Commit assegnati      : " + commitsConRelease);
        logger.logInfo("[CONSISTENCY] Commit non assegnati  : " + commitsSenzaRelease);
        logger.logInfo("[CONSISTENCY] Classi totali         : " + params.allClasses.size()
                + " (nomi distinti: " + classiUniche + ")");
        logger.logInfo("[CONSISTENCY] Classi buggy          : " + params.classiBuggy
                + " (" + String.format("%.1f", 100.0 * params.classiBuggy / params.allClasses.size()) + "%)");
        logger.logInfo("[CONSISTENCY] Ticket usati          : " + params.ticketsConCommit
                + "/" + params.ticketsConIV + " con IV");
        logger.logInfo("[CONSISTENCY] Record CSV scritti    : " + params.records.size());
        logger.logInfo("[CONSISTENCY] ==========================================");
    }

    // Trova lo SHA dell'ultimo commit prima o uguale alla data di release
    private String findLastCommitSha(List<GitCommit> commits, ProjectVersion release) {
        return commits.stream()
                .filter(c -> c.getDate() != null && !c.getDate().isAfter(release.getReleaseDate()))
                .max((a, b) -> a.getDate().compareTo(b.getDate()))
                .map(GitCommit::getSha)
                .orElse(null);
    }

    // Assegna un commit alla release che esso contribuisce a costruire:
    // intervallo (release precedente, release corrente]
    private ProjectVersion assignCommitToRelease(List<ProjectVersion> releases,
                                                 LocalDate commitDate) {
        for (int i = 0; i < releases.size(); i++) {
            ProjectVersion current = releases.get(i);
            ProjectVersion previous = (i == 0) ? null : releases.get(i - 1);

            boolean afterPrevious = previous == null || commitDate.isAfter(previous.getReleaseDate());
            boolean onOrBeforeCurrent = !commitDate.isAfter(current.getReleaseDate());

            if (afterPrevious && onOrBeforeCurrent) {
                return current;
            }
        }
        return null;
    }
}