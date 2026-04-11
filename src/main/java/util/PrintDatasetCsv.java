package util;



import domain.ClassRecord;
import util.ProgressLogger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class PrintDatasetCsv {

    private static final String OUTPUT_PATH = "file/Dataset.csv";
    private static final String HEADER = "release,className,loc,buggy";
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
                record.isBuggy() ? "yes" : "no");
    }
}
