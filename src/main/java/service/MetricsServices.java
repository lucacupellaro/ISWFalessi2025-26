package service;

import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.document.FileId;
import net.sourceforge.pmd.reporting.Report;
import util.ProgressLogger;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import java.util.*;

public class MetricsServices {

    private final ProgressLogger logger;
    private final LocalGitService localGitService;

    public MetricsServices(ProgressLogger logger, LocalGitService localGitService) {
        this.logger = logger;
        this.localGitService = localGitService;
    }

    // Classe privata dentro MetricsServices
    private record PmdMetrics(int smells, int cyclomaticComplexity, int duplication) {}

    public void computeAllMetrics(List<JavaClass> classes,
                                  List<GitCommit> commits,
                                  ProjectVersion release,
                                  String previousReleaseSha) {

        List<GitCommit> releaseCommits = commits.stream()
                .filter(c -> c.getDate() != null
                        && !c.getDate().isAfter(release.getReleaseDate()))
                .toList();

        computeLocAndComments(classes);
        computeNRevisions(classes, releaseCommits);
        computeNAuth(classes, releaseCommits);
        computeLocMetrics(classes, releaseCommits);
        computeChangeSet(classes, releaseCommits);
        computeAge(classes, releaseCommits, release);
        computeStructuralMetrics(classes);
        computeSmells(classes, previousReleaseSha);
    }

    // Change Set Size: numero di file committati insieme nei commit che toccano la classe
// Semantica *: from release 0 fino alla release corrente (usa releaseCommits)
    private void computeChangeSet(List<JavaClass> classes, List<GitCommit> commits) {
        for (JavaClass javaClass : classes) {

            List<GitCommit> classCommits = commits.stream()
                    .filter(c -> c.getTouchedPaths() != null
                            && c.getTouchedPaths().contains(javaClass.getPath()))
                    .toList();

            if (classCommits.isEmpty()) {
                javaClass.setChangeSetSize(0);
                javaClass.setMaxChangeSet(0);
                javaClass.setAvgChangeSet(0.0);
                continue;
            }

            // per ogni commit che tocca la classe, il change set è la dimensione
            // totale di quel commit (quanti file ha toccato in totale)
            List<Integer> changeSets = classCommits.stream()
                    .map(c -> c.getTouchedPaths().size())
                    .toList();

            int sum = changeSets.stream().mapToInt(Integer::intValue).sum();
            int max = changeSets.stream().mapToInt(Integer::intValue).max().orElse(0);
            double avg = changeSets.stream().mapToInt(Integer::intValue).average().orElse(0.0);

            javaClass.setChangeSetSize(sum);
            javaClass.setMaxChangeSet(max);
            javaClass.setAvgChangeSet(avg);
        }
    }

    // Age: settimane dal primo commit che ha toccato la classe fino alla release corrente
// Weighted Age: age ponderata per LOC touched per commit
// Semantica: NON ha *, si riferisce alla release corrente come punto di riferimento
    private void computeAge(List<JavaClass> classes,
                            List<GitCommit> commits,
                            ProjectVersion release) {

        for (JavaClass javaClass : classes) {

            List<GitCommit> classCommits = commits.stream()
                    .filter(c -> c.getTouchedPaths() != null
                            && c.getTouchedPaths().contains(javaClass.getPath())
                            && c.getDate() != null)
                    .toList();

            if (classCommits.isEmpty()) {
                javaClass.setAge(0.0);
                javaClass.setWeightedAge(0.0);
                continue;
            }

            LocalDate releaseDate = release.getReleaseDate();

            // Age = settimane tra il commit più vecchio che ha toccato la classe e la release
            LocalDate firstTouch = classCommits.stream()
                    .map(GitCommit::getDate)
                    .min(LocalDate::compareTo)
                    .orElse(releaseDate);

            long weeks = ChronoUnit.WEEKS.between(firstTouch, releaseDate);
            javaClass.setAge(weeks);

            // Weighted Age = Σ(weeksBeforeRelease_i * locTouched_i) / totalLocTouched
            long totalLocTouched = 0;
            double weightedSum = 0.0;

            for (GitCommit commit : classCommits) {
                List<Integer> diff = commit.getFileDiffs()
                        .getOrDefault(javaClass.getPath(), List.of(0, 0));

                int locTouched = diff.get(0) + diff.get(1);
                long weeksBeforeRelease = ChronoUnit.WEEKS.between(commit.getDate(), releaseDate);

                weightedSum += weeksBeforeRelease * locTouched;
                totalLocTouched += locTouched;
            }

            if (totalLocTouched > 0) {
                javaClass.setWeightedAge(weightedSum / totalLocTouched);
            } else {
                // nessuna LOC touched: fallback sull'age semplice
                javaClass.setWeightedAge(javaClass.getAge());
            }
        }
    }

    public void computeSmellsFromContent(List<JavaClass> classes) {
        for (JavaClass javaClass : classes) {
            String content = javaClass.getContent();
            if (content == null) {
                setDefaultSmells(javaClass);
                continue;
            }
            try {
                PmdMetrics metrics = runPmd(content, javaClass.getName());
                javaClass.setNSmells(metrics.smells());
                javaClass.setCyclomaticComplexity(metrics.cyclomaticComplexity());
                javaClass.setDuplication(metrics.duplication());
            } catch (Exception e) {
                logger.logWarning("PMD error per classe " + javaClass.getName() + ": " + e.getMessage());
                setDefaultSmells(javaClass);
            }
        }
    }

    private void computeSmells(List<JavaClass> classes, String previousReleaseSha) {
        for (JavaClass javaClass : classes) {
            // Per la prima release o classe non esistente nella release precedente
            String prevContent = (previousReleaseSha != null)
                    ? localGitService.readFileContent(previousReleaseSha, javaClass.getPath())
                    : null;

            if (prevContent == null) {
                setDefaultSmells(javaClass);
                continue;
            }

            try {
                PmdMetrics metrics = runPmd(prevContent, javaClass.getName());
                javaClass.setNSmells(metrics.smells());
                javaClass.setCyclomaticComplexity(metrics.cyclomaticComplexity());
                javaClass.setDuplication(metrics.duplication());
            } catch (Exception e) {
                logger.logWarning("PMD error per classe "
                        + javaClass.getName() + " @ release precedente: " + e.getMessage());
                setDefaultSmells(javaClass);
            }
        }
    }

    private void setDefaultSmells(JavaClass javaClass) {
        javaClass.setNSmells(0);
        javaClass.setCyclomaticComplexity(0);
        javaClass.setDuplication(0);
    }

    // Sostituisci runPmd con questa versione che ritorna più metriche
    private PmdMetrics runPmd(String sourceCode, String className) {
        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(
                net.sourceforge.pmd.lang.java.JavaLanguageModule.getInstance()
                        .getVersion("21"));
        config.addRuleSet("category/java/design.xml");
        config.addRuleSet("category/java/bestpractices.xml");
        config.addRuleSet("category/java/errorprone.xml"); // copre duplicazioni

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            FileId fileId = FileId.fromPathLikeString(className + ".java");
            pmd.files().addSourceFile(fileId, sourceCode);

            Report report = pmd.performAnalysisAndCollectReport();

            int smells = 0;
            int cyclomaticViolations = 0;
            int duplicationViolations = 0;

            for (var violation : report.getViolations()) {
                String ruleName = violation.getRule().getName();
                smells++;

                if (ruleName.contains("CyclomaticComplexity")
                        || ruleName.contains("NPathComplexity")) {
                    cyclomaticViolations++;
                }

                if (ruleName.contains("AvoidDuplicateLiterals")
                        || ruleName.contains("DuplicatedBlocks")) {
                    duplicationViolations++;
                }
            }

            return new PmdMetrics(smells, cyclomaticViolations, duplicationViolations);
        }
    }
    // LOC e commentLines calcolati dal contenuto reale del file
    private void computeLocAndComments(List<JavaClass> classes) {
        for (JavaClass javaClass : classes) {
            String content = javaClass.getContent();
            if (content == null) {
                javaClass.setLoc(0);
                javaClass.setCommentLines(0);
                continue;
            }

            String[] lines = content.split("\n", -1);
            int loc = 0;
            int comments = 0;
            boolean inBlockComment = false;

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                LineClassification classification = classifyLine(trimmed, inBlockComment);
                inBlockComment = classification.inBlockComment();
                if (classification.isComment()) {
                    comments++;
                } else {
                    loc++;
                }
            }

            javaClass.setLoc(loc);
            javaClass.setCommentLines(comments);
        }
    }

    private record LineClassification(boolean isComment, boolean inBlockComment) {}

    private LineClassification classifyLine(String trimmed, boolean inBlockComment) {
        if (inBlockComment) {
            boolean endsBlock = trimmed.contains("*/");
            return new LineClassification(true, !endsBlock);
        }
        if (trimmed.startsWith("/*")) {
            boolean singleLineBlock = trimmed.contains("*/");
            return new LineClassification(true, !singleLineBlock);
        }
        if (trimmed.startsWith("//")) {
            return new LineClassification(true, false);
        }
        return new LineClassification(false, false);
    }

    // NR — numero di commit che hanno toccato la classe (fino alla release corrente)
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

    // NAuth — numero di autori distinti che hanno toccato la classe (fino alla release corrente)
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
    // Aggiunge computeStructuralMetrics alla pipeline in computeAllMetrics
    private void computeStructuralMetrics(List<JavaClass> classes) {
        for (JavaClass javaClass : classes) {
            String content = javaClass.getContent();
            if (content == null) {
                javaClass.setNBranches(0);
                javaClass.setMaxNestingDepth(0);
                continue;
            }

            javaClass.setNBranches(countBranches(content));
            javaClass.setMaxNestingDepth(computeMaxNestingDepth(content));
        }
    }

    private int countBranches(String content) {
        // Conta tutti i decision point espliciti nel sorgente
        String[] keywords = {"if", "else if", "for", "while", "do", "case"};
        int count = 0;
        for (String kw : keywords) {
            int idx = 0;
            while ((idx = content.indexOf(kw, idx)) != -1) {
                // Verifica che sia una keyword e non parte di un identificatore
                boolean beforeOk = idx == 0
                        || !Character.isLetterOrDigit(content.charAt(idx - 1));
                boolean afterOk = idx + kw.length() >= content.length()
                        || !Character.isLetterOrDigit(content.charAt(idx + kw.length()));
                if (beforeOk && afterOk) {
                    count++;
                }
                idx += kw.length();
            }
        }
        return count;
    }

    private int computeMaxNestingDepth(String content) {
        NestingState state = new NestingState();

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = (i + 1 < content.length()) ? content.charAt(i + 1) : 0;

            int charsToSkip = state.processChar(c, next);
            i += charsToSkip;
        }
        return state.getMaxDepth();
    }

    /** Mutable state tracker for nesting depth computation. */
    private static class NestingState {
        private int maxDepth = 0;
        private int currentDepth = 0;
        private boolean inString = false;
        private boolean inChar = false;
        private boolean inLineComment = false;
        private boolean inBlockComment = false;

        int getMaxDepth() {
            return maxDepth;
        }

        /**
         * Processes a character and returns how many additional characters to skip.
         */
        int processChar(char c, char next) {
            if (inLineComment) {
                return processLineComment(c);
            }
            if (inBlockComment) {
                return processBlockComment(c, next);
            }
            if (inString) {
                return processString(c);
            }
            if (inChar) {
                return processCharLiteral(c);
            }
            return processCode(c, next);
        }

        private int processLineComment(char c) {
            if (c == '\n') {
                inLineComment = false;
            }
            return 0;
        }

        private int processBlockComment(char c, char next) {
            if (c == '*' && next == '/') {
                inBlockComment = false;
                return 1; // skip the '/'
            }
            return 0;
        }

        private int processString(char c) {
            if (c == '\\') {
                return 1; // skip escaped char
            }
            if (c == '"') {
                inString = false;
            }
            return 0;
        }

        private int processCharLiteral(char c) {
            if (c == '\\') {
                return 1; // skip escaped char
            }
            if (c == '\'') {
                inChar = false;
            }
            return 0;
        }

        private int processCode(char c, char next) {
            if (c == '/' && next == '/') {
                inLineComment = true;
                return 0;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                return 0;
            }
            if (c == '"') {
                inString = true;
                return 0;
            }
            if (c == '\'') {
                inChar = true;
                return 0;
            }
            if (c == '{') {
                currentDepth++;
                if (currentDepth > maxDepth) {
                    maxDepth = currentDepth;
                }
            } else if (c == '}') {
                currentDepth--;
            }
            return 0;
        }
    }

    private int getMax(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    private double getAvg(List<Integer> list) {
        return list.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
}