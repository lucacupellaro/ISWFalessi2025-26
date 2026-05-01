package service;

import domain.GitCommit;
import util.ProgressLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

public class LocalGitService {

    private final Path repoDir;
    private final ProgressLogger logger;

    public LocalGitService(String repoName, ProgressLogger logger) throws IOException, InterruptedException {
        this.logger = logger;
        this.repoDir = Path.of(System.getProperty("java.io.tmpdir"), "isw2-repos", repoName);

        if (Files.exists(repoDir.resolve("HEAD"))) {
            logger.logInfo("Repo già clonato in " + repoDir);
        } else {
            Files.createDirectories(repoDir.getParent());
            String url = "https://github.com/apache/" + repoName + ".git";
            logger.logInfo("Clono " + url + " in " + repoDir + " ...");
            runGit("git", "clone", "--bare", url, repoDir.toString());
            logger.logInfo("Clone completato.");
        }
    }

    /**
     * Restituisce TUTTI i commit del repo con autore, data, messaggio,
     * file toccati e diff (additions/deletions) per ogni file .java non-test.
     * Un singolo comando git, zero chiamate API.
     */
    public List<GitCommit> fetchAllCommits() throws IOException, InterruptedException {
        // Formato: SHA|autore|data|messaggio (una riga), seguiti dalle righe numstat
        // Il separatore "---COMMIT---" delimita i commit
        String output = runGit("git", "--git-dir", repoDir.toString(),
                "log", "--all", "--numstat",
                "--format=---COMMIT---%n%H|%an|%ad|%s", "--date=short");

        List<GitCommit> commits = new ArrayList<>();
        String[] blocks = output.split("---COMMIT---");

        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;

            String[] lines = block.split("\n");
            if (lines.length == 0) continue;

            // Prima riga: SHA|autore|data|messaggio
            String header = lines[0].trim();
            String[] parts = header.split("\\|", 4);
            if (parts.length < 4) continue;

            String sha = parts[0];
            String author = parts[1];
            String dateStr = parts[2];
            String message = parts[3];

            LocalDate date;
            try {
                date = LocalDate.parse(dateStr);
            } catch (Exception e) {
                continue;
            }

            GitCommit commit = new GitCommit(sha, message, date);
            commit.setAuthor(author);

            // Righe successive: numstat -> "added\tremoved\tpath"
            List<String> touchedPaths = new ArrayList<>();
            Map<String, List<Integer>> fileDiffs = new HashMap<>();

            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;

                String[] statParts = line.split("\t");
                if (statParts.length < 3) continue;

                String path = statParts[2];
                if (!path.endsWith(".java") || path.contains("/test/")) continue;

                // "-" indica file binari, li trattiamo come 0
                int added = statParts[0].equals("-") ? 0 : Integer.parseInt(statParts[0]);
                int removed = statParts[1].equals("-") ? 0 : Integer.parseInt(statParts[1]);

                touchedPaths.add(path);
                fileDiffs.put(path, List.of(added, removed));
            }

            commit.setTouchedPaths(touchedPaths);
            commit.setFileDiffs(fileDiffs);
            commits.add(commit);
        }

        logger.logInfo("Commit letti dal repo locale: " + commits.size());
        return commits;
    }

    /** Elenca tutti i file .java (esclusi i test) presenti al commit indicato. */
    public List<String> listJavaFiles(String sha) throws IOException, InterruptedException {
        String output = runGit("git", "--git-dir", repoDir.toString(),
                "ls-tree", "-r", "--name-only", sha);

        List<String> classes = new ArrayList<>();
        for (String line : output.split("\n")) {
            String path = line.trim();
            if (path.endsWith(".java") && !path.contains("/test/")) {
                classes.add(path);
            }
        }
        return classes;
    }

    /** Legge il contenuto di un file a un dato commit. */
    public String readFileContent(String sha, String path) {
        try {
            return runGit("git", "--git-dir", repoDir.toString(),
                    "show", sha + ":" + path);
        } catch (Exception e) {
            logger.logWarning("Errore lettura locale " + path + " @ " + sha + ": " + e.getMessage());
            return null;
        }
    }

    private String runGit(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git command failed (exit " + exitCode + "): " + output);
        }
        return output;
    }
}
