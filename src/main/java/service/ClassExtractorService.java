package service;

import client.GitHubCommitClient;
import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;
import util.ProgressLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClassExtractorService {

    private final GitHubCommitClient gitHubCommitClient;
    private final ProgressLogger logger;

    public ClassExtractorService(GitHubCommitClient gitHubCommitClient, ProgressLogger logger) {
        this.gitHubCommitClient = gitHubCommitClient;
        this.logger = logger;
    }

    public List<JavaClass> extractClassesForRelease(ProjectVersion release,
                                                    List<GitCommit> commits,
                                                    String repoName)
            throws IOException, InterruptedException {

        Optional<GitCommit> lastCommit = findLastCommitForRelease(commits, release);

        if (lastCommit.isEmpty()) {
            logger.logWarning("Nessun commit trovato per release: " + release.getName());
            return new ArrayList<>();
        }

        List<String> paths = gitHubCommitClient.fetchJavaClasses(repoName, lastCommit.get().getSha());
        List<JavaClass> classes = new ArrayList<>();

        for (String path : paths) {
            String name = extractClassName(path);
            classes.add(new JavaClass(name, path, release.getName()));
        }

        logger.logInfo("Release " + release.getName() + " — classi trovate: " + classes.size());
        return classes;
    }

    private Optional<GitCommit> findLastCommitForRelease(List<GitCommit> commits,
                                                         ProjectVersion release) {
        return commits.stream()
                .filter(c -> !c.getDate().isAfter(release.getReleaseDate()))
                .max((a, b) -> a.getDate().compareTo(b.getDate()));
    }

    private String extractClassName(String path) {
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return fileName.replace(".java", "");
    }
}