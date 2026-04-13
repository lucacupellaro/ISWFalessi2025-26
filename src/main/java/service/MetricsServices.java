package service;

import domain.GitCommit;
import domain.JavaClass;
import util.ProgressLogger;

import java.util.*;

public class MetricsServices {

    private final ProgressLogger logger;

    public MetricsServices(ProgressLogger logger) {
        this.logger = logger;
    }

    public void computeAllMetrics(List<JavaClass> classes,
                                  List<GitCommit> commits,
                                  String repoName) {
        computeLocFromDiffs(classes, commits);
        computeNRevisions(classes, commits);
        computeNAuth(classes, commits);
        computeLocMetrics(classes, commits);
    }

    // LOC stimato come somma cumulativa di (additions - deletions) su tutti i commit della storia del file
    private void computeLocFromDiffs(List<JavaClass> classes, List<GitCommit> commits) {
        Map<String, Integer> locPerPath = new HashMap<>();

        for (GitCommit commit : commits) {
            if (commit.getFileDiffs() == null) continue;
            commit.getFileDiffs().forEach((path, diff) -> {
                int net = diff.get(0) - diff.get(1); // additions - deletions
                locPerPath.merge(path, net, Integer::sum);
            });
        }

        for (JavaClass javaClass : classes) {
            int estimatedLoc = Math.max(0, locPerPath.getOrDefault(javaClass.getPath(), 0));
            javaClass.setLoc(estimatedLoc);
            javaClass.setCommentLines(0);
        }
    }

    // NR — numero di commit che hanno toccato la classe
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

    // NAuth — numero di autori distinti che hanno toccato la classe
    private void computeNAuth(List<JavaClass> classes, List<GitCommit> commits) {
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

    // LOC Added, Churn, LOC Touched, Max/Avg LOC Added, Max/Avg Churn
    // Legge le diff già scaricate da fetchTouchedPathsAndDiffs, senza fare nuove chiamate HTTP.
    private void computeLocMetrics(List<JavaClass> classes, List<GitCommit> commits) {
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

            List<Integer> addedList  = javaClass.getLocAddedList();
            List<Integer> churnList  = computeChurnList(addedList, javaClass.getLocRemovedList());

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