package service;



import client.GitHubCommitClient;
import domain.GitCommit;
import domain.JavaClass;
import util.ProgressLogger;

import java.io.IOException;
import java.util.*;;

public class MetricsServices {

    private final GitHubCommitClient gitHubCommitClient;
    private final ProgressLogger logger;

    public MetricsServices(GitHubCommitClient gitHubCommitClient, ProgressLogger logger) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.logger = logger;
    }

    public void computeAllMetrics(List<JavaClass> classes,
                                  List<GitCommit> commits,
                                  String repoName,
                                  String lastSha) throws IOException, InterruptedException {
        fetchContent(classes, repoName, lastSha);
        computeLoc(classes);
        computeCommentLines(classes);
        computeNRevisions(classes, commits);
        computeNAuth(classes, commits, repoName);
        computeLocMetrics(classes, commits, repoName);
    }

    private void fetchContent(List<JavaClass> classes, String repoName,
                              String sha) throws IOException, InterruptedException {
        for (JavaClass javaClass : classes) {
            String content = gitHubCommitClient.fetchFileContent(
                    repoName, javaClass.getPath(), sha);
            javaClass.setContent(content);
        }
    }

    // 1. LOC — conta le righe totali del file
    private void computeLoc(List<JavaClass> classes) {
        for (JavaClass javaClass : classes) {
            if (javaClass.getContent() == null) continue;
            String[] lines = javaClass.getContent().split("\r\n|\r|\n");
            javaClass.setLoc(lines.length);
        }
    }

    // 2. Comment Lines — conta le righe di commento
    private void computeCommentLines(List<JavaClass> classes) {
        for (JavaClass javaClass : classes) {
            if (javaClass.getContent() == null) continue;
            int count = 0;
            String[] lines = javaClass.getContent().split("\r\n|\r|\n");
            for (String line : lines) {
                String trim = line.trim();
                if (trim.startsWith("//") || trim.startsWith("*")
                        || trim.startsWith("/*") || trim.startsWith("*/")) {
                    count++;
                }
            }
            javaClass.setCommentLines(count);
        }
    }

    // 3. NR — numero di commit che hanno toccato la classe
    // Costruisce una mappa path → count in un singolo passaggio sui commit (O(commit)),
    // poi assegna i valori alle classi (O(classi)).
    private void computeNRevisions(List<JavaClass> classes, List<GitCommit> commits) {
        Map<String, Integer> revisionsPerPath = new HashMap<>();

        for (GitCommit commit : commits) {
            if (commit.getTouchedPaths() == null) continue;
            for (String path : commit.getTouchedPaths()) {
                revisionsPerPath.merge(path, 1, Integer::sum);
            }
        }

        for (JavaClass javaClass : classes) {
            javaClass.setNRevisions(revisionsPerPath.getOrDefault(javaClass.getPath(), 0));
        }
    }

    // 4. NAuth — numero di autori distinti che hanno toccato la classe
    // Costruisce una mappa path → Set<author> in un singolo passaggio sui commit (O(commit)),
    // poi assegna i valori alle classi (O(classi)).
    private void computeNAuth(List<JavaClass> classes,
                              List<GitCommit> commits,
                              String repoName) {
        Map<String, Set<String>> authorsPerPath = new HashMap<>();

        for (GitCommit commit : commits) {
            if (commit.getTouchedPaths() == null || commit.getAuthor() == null) continue;
            for (String path : commit.getTouchedPaths()) {
                authorsPerPath.computeIfAbsent(path, k -> new HashSet<>())
                        .add(commit.getAuthor());
            }
        }

        for (JavaClass javaClass : classes) {
            Set<String> authors = authorsPerPath.getOrDefault(javaClass.getPath(), Collections.emptySet());
            javaClass.setNAuth(authors.size());
        }
    }

    // 5-11. LOC Added, Churn, LOC Touched, Max/Avg LOC Added, Max/Avg Churn
    // Legge le diff già scaricate da fetchTouchedPathsAndDiffs, senza fare nuove chiamate HTTP.
    private void computeLocMetrics(List<JavaClass> classes,
                                   List<GitCommit> commits,
                                   String repoName) {
        for (JavaClass javaClass : classes) {

            List<GitCommit> classCommits = commits.stream()
                    .filter(c -> c.getTouchedPaths() != null
                            && c.getTouchedPaths().contains(javaClass.getPath()))
                    .toList();

            for (GitCommit commit : classCommits) {
                List<Integer> diff = commit.getFileDiffs()
                        .getOrDefault(javaClass.getPath(), List.of(0, 0));

                int added   = diff.get(0);
                int removed = diff.get(1);
                int churn   = Math.abs(added - removed);
                int touched = added + removed;

                javaClass.addLocAdded(added);
                javaClass.addLocRemoved(removed);
                javaClass.sumLocAdded(added);
                javaClass.sumChurn(churn);
                javaClass.addLocTouched(touched);
            }

            List<Integer> addedList = javaClass.getLocAddedList();
            List<Integer> churnList = computeChurnList(
                    javaClass.getLocAddedList(), javaClass.getLocRemovedList());

            javaClass.setMaxLocAdded(getMax(addedList));
            javaClass.setMaxChurn(getMax(churnList));
            javaClass.setAvgLocAdded(getAvg(addedList));
            javaClass.setAvgChurn(getAvg(churnList));
        }
    }

    private List<Integer> computeChurnList(List<Integer> added, List<Integer> removed) {
        List<Integer> churn = new ArrayList<>();
        for (int i = 0; i < added.size(); i++) {
            churn.add(Math.abs(added.get(i) - removed.get(i)));
        }
        return churn;
    }

    private int getMax(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private double getAvg(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}