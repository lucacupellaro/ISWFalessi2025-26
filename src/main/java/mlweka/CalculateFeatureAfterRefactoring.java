package mlweka;

import domain.GitCommit;
import domain.JavaClass;
import domain.ProjectVersion;
import service.MetricsServices;
import util.ProgressLogger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CalculateFeatureAfterRefactoring {

    private static final Logger logger = Logger.getLogger(CalculateFeatureAfterRefactoring.class.getName());

    private static final String CSV_DIR = "src/main/java/file/Milestone4";
    private static final String HEADER = "className,loc,commentLines,nRevisions,nAuth,nFix," +
            "locAdded,maxLocAdded,avgLocAdded,churn,maxChurn,avgChurn,locTouched," +
            "changeSetSize,maxChangeSet,avgChangeSet,age,weightedAge," +
            "cyclomaticComplexity,duplication,nBranches,maxNestingDepth,nSmellsPmd,nSmellsSonar";

    private final MetricsServices metricsServices;
    private final String csvFile;
    private final GitMetrics gitMetrics;

    /**
     * Metriche git calcolate dalla storia del repo ZooKeeper fino al commit 6c4fabfd.
     * Identiche per versione originale e refactorizzata (il refactoring non modifica la storia git).
     */
    record GitMetrics(int nRevisions, int nAuth, int nFix,
                      int locAdded, int maxLocAdded, double avgLocAdded,
                      int churn, int maxChurn, double avgChurn,
                      int locTouched,
                      int changeSetSize, int maxChangeSet, double avgChangeSet,
                      double age, double weightedAge) {}

    private static final Map<String, GitMetrics> METRICS_PER_CLASS = Map.of(
            "DataTree", new GitMetrics(129, 36, 19,
                    3866, 516, 31.69,
                    2794, 516, 22.90,
                    5748,
                    6119, 1187, 47.43,
                    961.69, 614.76),
            "PathTrie", new GitMetrics(13, 10, 2,
                    1471, 346, 113.15,
                    356, 280, 27.38,
                    2588,
                    2745, 1187, 211.15,
                    896.14, 430.27)
    );

    public CalculateFeatureAfterRefactoring(String csvFile, String classKey) {
        ProgressLogger progressLogger = new ProgressLogger();
        this.metricsServices = new MetricsServices(progressLogger, null);
        this.csvFile = csvFile;
        this.gitMetrics = METRICS_PER_CLASS.get(classKey);
        if (this.gitMetrics == null) {
            throw new IllegalArgumentException("Nessuna metrica git definita per la classe: " + classKey);
        }
    }

    public void calculateAndAppend(String filePath) throws IOException {
        Path path = Path.of(filePath);
        String content = Files.readString(path);
        String className = path.getFileName().toString().replace(".java", "");

        JavaClass javaClass = new JavaClass(className, filePath, "after-refactoring");
        javaClass.setContent(content);

        List<JavaClass> classList = List.of(javaClass);
        List<GitCommit> emptyCommits = Collections.emptyList();
        ProjectVersion dummyRelease = new ProjectVersion("after-refactoring", LocalDate.now(ZoneId.systemDefault()));

        metricsServices.computeAllMetrics(classList, emptyCommits, dummyRelease, null);
        metricsServices.computeSmellsFromContent(classList);
        setGitMetrics(javaClass);

        appendToCsv(javaClass);
    }

    private void setGitMetrics(JavaClass jc) {
        jc.setNRevisions(gitMetrics.nRevisions());
        jc.setNAuth(gitMetrics.nAuth());
        jc.setNFix(gitMetrics.nFix());
        jc.sumLocAdded(gitMetrics.locAdded());
        jc.setMaxLocAdded(gitMetrics.maxLocAdded());
        jc.setAvgLocAdded(gitMetrics.avgLocAdded());
        jc.sumChurn(gitMetrics.churn());
        jc.setMaxChurn(gitMetrics.maxChurn());
        jc.setAvgChurn(gitMetrics.avgChurn());
        jc.addLocTouched(gitMetrics.locTouched());
        jc.setChangeSetSize(gitMetrics.changeSetSize());
        jc.setMaxChangeSet(gitMetrics.maxChangeSet());
        jc.setAvgChangeSet(gitMetrics.avgChangeSet());
        jc.setAge(gitMetrics.age());
        jc.setWeightedAge(gitMetrics.weightedAge());
    }

    public static void main(String[] args) {
        try {
            // === DataTree ===
            runForClass(
                    CSV_DIR + "/MetricheDopoRefactoring.csv",
                    "DataTree",
                    "tmp/DataTreeRefactored/DataTree_original.java",
                    "tmp/DataTreeRefactored/DataTree_C4.java"
            );

            // === PathTrie ===
            runForClass(
                    CSV_DIR + "/MetricheDopoRefactoringPathTrie.csv",
                    "PathTrie",
                    "tmp/PathTrieRefactored/PathTriee_original.java",
                    "tmp/PathTrieRefactored/PathTrie_c4.java"
            );

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Errore: {0}", e.getMessage());
            System.exit(1);
        }
    }

    private static void runForClass(String csvPath, String classKey,
                                    String originalPath, String refactoredPath) throws IOException {
        CalculateFeatureAfterRefactoring calculator = new CalculateFeatureAfterRefactoring(csvPath, classKey);

        File outputCsvFile = new File(csvPath);
        if (!outputCsvFile.exists() || outputCsvFile.length() == 0) {
            calculator.calculateAndAppend(originalPath);
            logger.log(Level.INFO, "Metriche originale aggiunte per: {0}", originalPath);
        } else {
            logger.log(Level.INFO, "CSV già presente, salto classe originale per: {0}", classKey);
        }

        calculator.calculateAndAppend(refactoredPath);
        logger.log(Level.INFO, "Metriche refactorizzate aggiunte per: {0}", refactoredPath);
    }

    private void appendToCsv(JavaClass jc) throws IOException {
        File localCsvFile = new File(this.csvFile);
        boolean writeHeader = !localCsvFile.exists() || localCsvFile.length() == 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(localCsvFile, true))) {
            if (writeHeader) {
                pw.println(HEADER);
            }
            pw.printf(java.util.Locale.US, "%s,%d,%d,%d,%d,%d,%d,%d,%.2f,%d,%d,%.2f,%d,%d,%d,%.2f,%.2f,%.2f,%d,%d,%d,%d,%d,%n",
                    jc.getName(),
                    jc.getLoc(),
                    jc.getCommentLines(),
                    jc.getNRevisions(),
                    jc.getNAuth(),
                    jc.getNFix(),
                    jc.getLocAdded(),
                    jc.getMaxLocAdded(),
                    jc.getAvgLocAdded(),
                    jc.getChurn(),
                    jc.getMaxChurn(),
                    jc.getAvgChurn(),
                    jc.getLocTouched(),
                    jc.getChangeSetSize(),
                    jc.getMaxChangeSet(),
                    jc.getAvgChangeSet(),
                    jc.getAge(),
                    jc.getWeightedAge(),
                    jc.getCyclomaticComplexity(),
                    jc.getDuplication(),
                    jc.getNBranches(),
                    jc.getMaxNestingDepth(),
                    jc.getNSmells());
        }
    }
}