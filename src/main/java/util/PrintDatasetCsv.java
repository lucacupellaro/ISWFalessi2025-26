package util;

import domain.ClassRecord;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PrintDatasetCsv {

    private static final String OUTPUT_PATH = "src/main/java/file/DatasetClassiRelease.csv";
    private static final String HEADER = "release,className,loc,commentLines," +
            "nRevisions,nAuth,nFix,locAdded,maxLocAdded,avgLocAdded," +
            "churn,maxChurn,avgChurn,locTouched," +
            "changeSetSize,maxChangeSet,avgChangeSet,age,weightedAge," +
            "cyclomaticComplexity,duplication,nBranches,maxNestingDepth," +
            "nSmells,buggy";
    private final ProgressLogger logger = new ProgressLogger();

    public void write(List<ClassRecord> records) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_PATH))) {
            writer.write(HEADER);
            writer.newLine();
            for (ClassRecord classRecord : records) {
                writer.write(buildRow(classRecord));
                writer.newLine();
            }
        }
        logger.logInfo("Dataset.csv scritto con " + records.size() + " righe");
    }

    private String buildRow(ClassRecord classRecord) {
        return String.join(",",
                classRecord.getRelease(),
                classRecord.getClassName(),
                String.valueOf(classRecord.getLoc()),
                String.valueOf(classRecord.getCommentLines()),
                String.valueOf(classRecord.getNRevisions()),
                String.valueOf(classRecord.getNAuth()),
                String.valueOf(classRecord.getNFix()),
                String.valueOf(classRecord.getLocAdded()),
                String.valueOf(classRecord.getMaxLocAdded()),
                String.valueOf(classRecord.getAvgLocAdded()),
                String.valueOf(classRecord.getChurn()),
                String.valueOf(classRecord.getMaxChurn()),
                String.valueOf(classRecord.getAvgChurn()),
                String.valueOf(classRecord.getLocTouched()),
                String.valueOf(classRecord.getChangeSetSize()),
                String.valueOf(classRecord.getMaxChangeSet()),
                String.valueOf(classRecord.getAvgChangeSet()),
                String.valueOf(classRecord.getAge()),
                String.valueOf(classRecord.getWeightedAge()),
                String.valueOf(classRecord.getCyclomaticComplexity()),
                String.valueOf(classRecord.getDuplication()),
                String.valueOf(classRecord.getNBranches()),
                String.valueOf(classRecord.getMaxNestingDepth()),
                String.valueOf(classRecord.getNSmells()),
                classRecord.isBuggy() ? "yes" : "no");
    }
}