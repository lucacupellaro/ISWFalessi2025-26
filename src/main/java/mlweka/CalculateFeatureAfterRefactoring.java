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
import java.util.Collections;
import java.util.List;

public class CalculateFeatureAfterRefactoring {

    private static final String CSV_DIR = "src/main/java/file/Milestone4";
    private static final String CSV_FILE = CSV_DIR + "/MetricheDopoRefactoringPathTrie.csv";
    private static final String HEADER = "className,loc,commentLines,nRevisions,nAuth,nFix," +
            "locAdded,maxLocAdded,avgLocAdded,churn,maxChurn,avgChurn,locTouched," +
            "changeSetSize,maxChangeSet,avgChangeSet,age,weightedAge," +
            "cyclomaticComplexity,duplication,nBranches,maxNestingDepth,nSmellsPmd,nSmellsSonar";

    private static final String CLASS_REFACTORED = "tmp/PathTrieRefactored/PathTrie_c4.java";
    private static final String CLASS_ORIGINAL = "tmp/PathTrieRefactored/PathTriee_original.java";

    private final MetricsServices metricsServices;

    public CalculateFeatureAfterRefactoring() {
        ProgressLogger logger = new ProgressLogger();
        this.metricsServices = new MetricsServices(logger, null);
    }

    public void calculateAndAppend(String filePath) throws IOException {
        Path path = Path.of(filePath);
        String content = Files.readString(path);
        String className = path.getFileName().toString().replace(".java", "");

        JavaClass javaClass = new JavaClass(className, filePath, "after-refactoring");
        javaClass.setContent(content);

        List<JavaClass> classList = List.of(javaClass);
        List<GitCommit> emptyCommits = Collections.emptyList();
        ProjectVersion dummyRelease = new ProjectVersion("after-refactoring", LocalDate.now());

        // Calcola LOC, commentLines, structuralMetrics (nBranches, maxNestingDepth)
        metricsServices.computeAllMetrics(classList, emptyCommits, dummyRelease, null);

        // Calcola smells, cyclomaticComplexity, duplication dal contenuto del file
        metricsServices.computeSmellsFromContent(classList);

        // Imposta le metriche git reali (calcolate dalla storia del repo ZooKeeper)
        setGitMetrics(javaClass);

        appendToCsv(javaClass);
    }

    /**
     * Metriche git calcolate dalla storia completa del repo ZooKeeper
     * fino al commit 6c4fabfd (prima delle modifiche utente).
     * Queste metriche sono identiche per la versione originale e refactorizzata
     * perché il refactoring non modifica la storia git precedente.
     */
    private void setGitMetrics(JavaClass jc) {
        jc.setNRevisions(129);
        jc.setNAuth(36);
        jc.setNFix(19);
        jc.sumLocAdded(3866);
        jc.setMaxLocAdded(516);
        jc.setAvgLocAdded(31.69);
        jc.sumChurn(2794);
        jc.setMaxChurn(516);
        jc.setAvgChurn(22.90);
        jc.addLocTouched(5748);
        jc.setChangeSetSize(6119);
        jc.setMaxChangeSet(1187);
        jc.setAvgChangeSet(47.43);
        jc.setAge(961.69);
        jc.setWeightedAge(614.76);
    }

    public static void main(String[] args) {
        try {
            CalculateFeatureAfterRefactoring calculator = new CalculateFeatureAfterRefactoring();

            // Riga 1: classe originale — aggiunta solo se il CSV non esiste ancora
            File csvFile = new File(CSV_FILE);
            if (!csvFile.exists() || csvFile.length() == 0) {
                calculator.calculateAndAppend(CLASS_ORIGINAL);
                System.out.println("Metriche originale aggiunte per: " + CLASS_ORIGINAL);
            } else {
                System.out.println("CSV già presente, salto classe originale.");
            }

            // Riga successiva: classe refactorizzata
            calculator.calculateAndAppend(CLASS_REFACTORED);
            System.out.println("Metriche refactorizzate aggiunte per: " + CLASS_REFACTORED);

        } catch (IOException e) {
            System.err.println("Errore: " + e.getMessage());
            System.exit(1);
        }
    }

    private void appendToCsv(JavaClass jc) throws IOException {
        File csvFile = new File(CSV_FILE);
        boolean writeHeader = !csvFile.exists() || csvFile.length() == 0;

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile, true))) {
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