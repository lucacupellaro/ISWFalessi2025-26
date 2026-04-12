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

        logger.logInfo("1 Scarico release reali da Jira per " + projectKey + "e applico il taglio..");
        List<ProjectVersion> windowedReleases = versionService
                .loadVersions(projectKey, windowPercent);
        logger.logInfo("Release nella finestra: " + windowedReleases.size());

        logger.logInfo("Scarico commit da GitHub per " + repoName + "...");
        List<GitCommit> commits = commitMapper.mapCommits(
                gitHubCommitClient.fetchAllCommits(projectConfig));
        logger.logInfo("Totale commit scaricati: " + commits.size());

        //ogni commit viene assegnato ala sua Realese
        for (GitCommit commit : commits) {
            ProjectVersion release = assignCommitToRelease(windowedReleases, commit.getDate());
            commit.setRelease(release);
        }

        //ricostruisce le classi Java esistenti in quella release
        logger.logInfo("Estrazione classi per release...");
        List<JavaClass> allClasses = new ArrayList<>();
        for (ProjectVersion release : windowedReleases) {
            List<JavaClass> classes = classExtractorService
                    .extractClassesForRelease(release, commits, repoName);
            allClasses.addAll(classes);
        }
        logger.logInfo("Totale classi estratte: " + allClasses.size());

        logger.logInfo("Avvio labeling...");
        List<BugTicketRecord> projectTickets = tickets.stream()
                .filter(t -> t.getProjectKey().equals(projectKey))
                .toList();

        //per ogni ticket,  trova i commit associati (ticketKey nel messaggio)
        for (BugTicketRecord ticket : projectTickets) {

            if (ticket.getInjectionVersion() == null) {
                logger.logWarning("Ticket " + ticket.getId() + " senza IV — saltato");
                continue;
            }

            //prende solo i commit che hanno nel messaggio il ticket considerato
            List<GitCommit> relevantCommits = commits.stream()
                    .filter(c -> c.getMessage().contains(ticket.getTicketKey()))
                    .toList();

            if (relevantCommits.isEmpty()) {
                logger.logWarning("Nessun commit trovato per ticket " + ticket.getTicketKey());
                continue;
            }

            //touchedPaths è la lista dei path delle classi Java modificate da quel commit
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
                    ticket.getInjectionVersion(),
                    ticket.getVersionRelation().getFixVersion());
        }

        logger.logInfo("Labeling completato per " + projectKey);

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

    //assegno i commit alla sua release
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