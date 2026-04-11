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
    private final LabelingService labelingService;
    private final PrintDatasetCsv printDatasetCsv;
    private final ProgressLogger logger;

    public Phase2Controller(VersionService versionService,
                            GitHubCommitClient gitHubCommitClient,
                            CommitMapper commitMapper,
                            ClassExtractorService classExtractorService,
                            LabelingService labelingService,
                            PrintDatasetCsv printDatasetCsv,
                            ProgressLogger logger) {
        this.versionService = versionService;
        this.gitHubCommitClient = gitHubCommitClient;
        this.commitMapper = commitMapper;
        this.classExtractorService = classExtractorService;
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

        // step 1 — scarica e finestra le release
        logger.logInfo("Scarico release reali da Jira per " + projectKey + "...");
        List<ProjectVersion> windowedReleases = versionService
                .loadVersions(projectKey, windowPercent);
        logger.logInfo("Release nella finestra: " + windowedReleases.size());

        // step 2 — scarica tutti i commit
        logger.logInfo("Scarico commit da GitHub per " + repoName + "...");
        List<GitCommit> commits = commitMapper.mapCommits(
                gitHubCommitClient.fetchAllCommits(projectConfig));

// LOG DI CONTROLLO — da rimuovere dopo il debug
        logger.logInfo("Totale commit scaricati: " + commits.size());
        commits.stream()
                .limit(5)
                .forEach(c -> logger.logInfo("Commit msg: " +
                        c.getMessage().substring(0, Math.min(80, c.getMessage().length()))));

// data del commit più vecchio e più recente
        commits.stream()
                .min((a, b) -> a.getDate().compareTo(b.getDate()))
                .ifPresent(c -> logger.logInfo("Commit più vecchio: " + c.getDate() + " — " +
                        c.getMessage().substring(0, Math.min(50, c.getMessage().length()))));
        commits.stream()
                .max((a, b) -> a.getDate().compareTo(b.getDate()))
                .ifPresent(c -> logger.logInfo("Commit più recente: " + c.getDate() + " — " +
                        c.getMessage().substring(0, Math.min(50, c.getMessage().length()))));

        long countWithKey = commits.stream()
                .filter(c -> c.getMessage().contains("ZOOKEEPER-"))
                .count();
        logger.logInfo("Commit con ZOOKEEPER- nel messaggio: " + countWithKey + "/" + commits.size());
        // step 3 — assegna ogni commit alla sua release
        for (GitCommit commit : commits) {
            ProjectVersion release = assignCommitToRelease(windowedReleases, commit.getDate());
            commit.setRelease(release);
        }

        // step 4 — per ogni release estrai le classi
        logger.logInfo("Estrazione classi per release...");
        List<JavaClass> allClasses = new ArrayList<>();
        for (ProjectVersion release : windowedReleases) {
            List<JavaClass> classes = classExtractorService
                    .extractClassesForRelease(release, commits, repoName);
            allClasses.addAll(classes);
        }
        logger.logInfo("Totale classi estratte: " + allClasses.size());

        // step 5 — labeling lazy: popola touchedPaths solo per commit con ticketId
        logger.logInfo("Avvio labeling...");
        List<BugTicketRecord> projectTickets = tickets.stream()
                .filter(t -> t.getProjectKey().equals(projectKey))
                .toList();

        for (BugTicketRecord ticket : projectTickets) {

            if (ticket.getInjectionVersion() == null) {
                logger.logWarning("Ticket " + ticket.getId() + " senza IV — saltato");
                continue;
            }

            logger.logInfo("Cerco commit per ticketKey: " + ticket.getTicketKey() + " id: " + ticket.getId());

            List<GitCommit> relevantCommits = commits.stream()
                    .filter(c -> c.getMessage().contains(ticket.getTicketKey()))
                    .toList();

            if (relevantCommits.isEmpty()) {
                logger.logWarning("Nessun commit trovato per ticket " + ticket.getId());
                continue;
            }

            for (GitCommit commit : relevantCommits) {
                if (commit.getTouchedPaths() == null) {
                    List<String> paths = gitHubCommitClient
                            .fetchTouchedPaths(repoName, commit.getSha());
                    commit.setTouchedPaths(paths);
                }
            }

            labelingService.assignLabels(
                    allClasses,
                    relevantCommits,
                    windowedReleases,
                    ticket.getTicketKey(),
                    ticket.getInjectionVersion(),
                    ticket.getVersionRelation().getFixVersion());
        }

        logger.logInfo("Labeling completato per " + projectKey);

        // step 6 — costruisci ClassRecord e scrivi CSV
        logger.logInfo("Scrittura dataset...");
        List<ClassRecord> records = allClasses.stream()
                .map(c -> new ClassRecord(
                        c.getRelease(),
                        c.getName(),
                        c.getLoc(),
                        c.isBuggy()))
                .toList();

        printDatasetCsv.write(records);
        logger.logInfo("Dataset scritto: " + records.size() + " righe");
    }

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