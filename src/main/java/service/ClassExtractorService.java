package service;

import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;
import util.ProgressLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClassExtractorService {

    private final LocalGitService localGitService;
    private final ProgressLogger logger;

    public ClassExtractorService(LocalGitService localGitService, ProgressLogger logger) {
        this.localGitService = localGitService;
        this.logger = logger;
    }

    //Data una release, trovato l'ultimo commit, ottiene i path di tutte le classi Java presenti nel repo a quel punto della storia.
    public List<JavaClass> extractClassesForRelease(ProjectVersion release,
                                                    List<GitCommit> commits)
            throws IOException, InterruptedException {

        Optional<GitCommit> lastCommit = findLastCommitForRelease(commits, release);

        if (lastCommit.isEmpty()) {
            logger.logWarning("Nessun commit trovato per release: " + release.getName());
            return new ArrayList<>();
        }

        String sha = lastCommit.get().getSha();
        List<String> paths = localGitService.listJavaFiles(sha);
        List<JavaClass> classes = new ArrayList<>();

        for (String path : paths) {
            String name = extractClassName(path);
            JavaClass jc = new JavaClass(name, path, release.getName());
            // legge il contenuto dal clone locale (nessuna chiamata API)
            String content = localGitService.readFileContent(sha, path);
            jc.setContent(content);
            classes.add(jc);
        }

        logger.logInfo("Release " + release.getName() + " — classi trovate: " + classes.size());
        return classes;
    }

    //trova l'ultimo commit appartenente a una release
    private Optional<GitCommit> findLastCommitForRelease(List<GitCommit> commits,
                                                         ProjectVersion release) {
        return commits.stream()
                .filter(c -> !c.getDate().isAfter(release.getReleaseDate()))
                .max((a, b) -> a.getDate().compareTo(b.getDate()));
    }

    //. Prende il path completo tipo zookeeper-server/src/main/java/org/apache/zookeeper/ZooKeeper.java e restituisce solo il nome della classe senza estensione,
    private String extractClassName(String path) {
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return fileName.replace(".java", "");
    }
}
