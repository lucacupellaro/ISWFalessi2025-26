package MLWeka;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.StratifiedRemoveFolds;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

public class DataEngeneering {

    private static final Logger logger = Logger.getLogger(DataEngeneering.class.getName());

    /**
     * Split stratificato 80/20: mescola le istanze e mantiene la stessa
     * proporzione di buggy sia nel training che nel test set.
     *
     * @return array di due elementi: [0] = training set, [1] = test set
     */
    public Instances[] stratifiedSplit(Instances data, int seed) throws Exception {
        // Mescola le istanze
        Instances shuffled = new Instances(data);
        shuffled.randomize(new Random(seed));
        // Stratifica per mantenere la distribuzione della classe
        shuffled.stratify(5); // 5 fold -> ogni fold = 20%

        // Prendi 1 fold come test (20%) e i restanti 4 come training (80%)
        // StratifiedRemoveFolds mantiene la proporzione della classe in ogni fold
        Instances trainSet = trainFold(shuffled);
        Instances testSet = testFold(shuffled);

        logger.info("Split stratificato completato:");
        logger.info("  Training set: " + trainSet.numInstances() + " istanze");
        logger.info("  Test set: " + testSet.numInstances() + " istanze");
        logClassDistribution("Training", trainSet);
        logClassDistribution("Test", testSet);

        return new Instances[]{trainSet, testSet};
    }

    private Instances trainFold(Instances data) throws Exception {
        StratifiedRemoveFolds filter = new StratifiedRemoveFolds();
        filter.setNumFolds(5);
        filter.setFold(1);
        filter.setInvertSelection(true); // tutti i fold TRANNE il primo -> 80%
        filter.setInputFormat(data);
        return Filter.useFilter(data, filter);
    }

    private Instances testFold(Instances data) throws Exception {
        StratifiedRemoveFolds filter = new StratifiedRemoveFolds();
        filter.setNumFolds(5);
        filter.setFold(1);
        filter.setInvertSelection(false); // solo il primo fold -> 20%
        filter.setInputFormat(data);
        return Filter.useFilter(data, filter);
    }

    /**
     * Normalizza le feature numeriche applicando log1p: x -> log(1 + x).
     * Ignora l'attributo classe (buggy).
     */
    public Instances normalizeLog1p(Instances data) {
        Instances normalized = new Instances(data);
        int classIndex = normalized.classIndex();

        for (int j = 0; j < normalized.numAttributes(); j++) {
            // Salta l'attributo classe e gli attributi non numerici
            if (j == classIndex || !normalized.attribute(j).isNumeric()) {
                continue;
            }
            for (int i = 0; i < normalized.numInstances(); i++) {
                double val = normalized.instance(i).value(j);
                normalized.instance(i).setValue(j, Math.log1p(val));
            }
        }

        logger.info("Normalizzazione log1p applicata su " +
                (normalized.numAttributes() - 1) + " feature numeriche");
        return normalized;
    }

    /**
     * Salva le Instances in formato CSV.
     */
    public void saveToCsv(Instances data, String path) throws IOException {
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

    private void logClassDistribution(String setName, Instances data) {
        int classIndex = data.classIndex();
        int[] counts = new int[data.numClasses()];
        for (int i = 0; i < data.numInstances(); i++) {
            counts[(int) data.instance(i).classValue()]++;
        }
        StringBuilder sb = new StringBuilder("  " + setName + " distribuzione: ");
        for (int c = 0; c < data.numClasses(); c++) {
            double pct = 100.0 * counts[c] / data.numInstances();
            sb.append(data.classAttribute().value(c))
              .append("=").append(counts[c])
              .append(String.format(" (%.1f%%)", pct));
            if (c < data.numClasses() - 1) sb.append(", ");
        }
        logger.info(sb.toString());
    }
}