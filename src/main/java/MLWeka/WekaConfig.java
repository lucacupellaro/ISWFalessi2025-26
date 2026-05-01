package MLWeka;

import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class WekaConfig {

    private static final Logger logger = Logger.getLogger(WekaConfig.class.getName());

    private WekaConfig() {
    }

    public static Instances loadDataset(String csvPath, boolean removeIdentifiers) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        if (removeIdentifiers) {
            // Rimuovi className (indice 1) e release (indice 0) — rimuovi prima l'indice più alto
            data.deleteAttributeAt(1);
            data.deleteAttributeAt(0);
        }

        // Imposta l'ultima colonna (buggy) come classe
        data.setClassIndex(data.numAttributes() - 1);

        logger.info("Dataset caricato: " + data.numInstances() + " istanze, "
                + data.numAttributes() + " attributi (classe: " + data.classAttribute().name() + ")");

        return data;
    }

    public static void saveToCsv(Instances data, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            StringBuilder header = new StringBuilder();
            for (int i = 0; i < data.numAttributes(); i++) {
                if (i > 0) header.append(",");
                header.append(data.attribute(i).name());
            }
            writer.write(header.toString());
            writer.newLine();

            for (int i = 0; i < data.numInstances(); i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < data.numAttributes(); j++) {
                    if (j > 0) row.append(",");
                    row.append(data.instance(i).toString(j));
                }
                writer.write(row.toString());
                writer.newLine();
            }
        }
        logger.info("Dataset salvato in: " + path);
    }
}