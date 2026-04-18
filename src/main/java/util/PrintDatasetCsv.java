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
            for (ClassRecord record : records) {
                writer.write(buildRow(record));
                writer.newLine();
            }
        }
        logger.logInfo("Dataset.csv scritto con " + records.size() + " righe");
    }

    private String buildRow(ClassRecord record) {
        return String.join(",",
                record.getRelease(),
                record.getClassName(),
                String.valueOf(record.getLoc()),
                String.valueOf(record.getCommentLines()),
                String.valueOf(record.getNRevisions()),
                String.valueOf(record.getNAuth()),
                String.valueOf(record.getNFix()),
                String.valueOf(record.getLocAdded()),
                String.valueOf(record.getMaxLocAdded()),
                String.valueOf(record.getAvgLocAdded()),
                String.valueOf(record.getChurn()),
                String.valueOf(record.getMaxChurn()),
                String.valueOf(record.getAvgChurn()),
                String.valueOf(record.getLocTouched()),
                String.valueOf(record.getChangeSetSize()),
                String.valueOf(record.getMaxChangeSet()),
                String.valueOf(record.getAvgChangeSet()),
                String.valueOf(record.getAge()),
                String.valueOf(record.getWeightedAge()),
                String.valueOf(record.getCyclomaticComplexity()),
                String.valueOf(record.getDuplication()),
                String.valueOf(record.getNBranches()),
                String.valueOf(record.getMaxNestingDepth()),
                String.valueOf(record.getNSmells()),
                record.isBuggy() ? "yes" : "no");
    }
}