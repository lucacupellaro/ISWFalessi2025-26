package controller;

import MLWeka.DataEngeneering;
import MLWeka.FeatureSelection;
import MLWeka.FeatureSelection.ExperimentResult;
import MLWeka.WekaConfig;
import weka.core.Instances;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class MLWekaController {

    private static final Logger logger = Logger.getLogger(MLWekaController.class.getName());
    private static final String DATASET_PATH = "src/main/java/file/DatasetClassiRelease.csv";

    public void run() throws Exception {
        logger.info("=== Avvio MLWeka Pipeline ===");

        // 1. Carica il dataset
        Instances data = WekaConfig.loadDataset(DATASET_PATH);

        // 2. Split stratificato 80/20
        DataEngeneering dataEng = new DataEngeneering();
        Instances[] split = dataEng.stratifiedSplit(data, 42);
        Instances trainSet = split[0];
        Instances testSet = split[1];

        // 3. Normalizzazione log1p
        trainSet = dataEng.normalizeLog1p(trainSet);
        testSet = dataEng.normalizeLog1p(testSet);

        // Salva il dataset normalizzato su CSV
        dataEng.saveToCsv(trainSet, "src/main/java/file/DatasetTrainingClassiRealeseNormalized.csv");

        // 4. Feature Selection: 3 classificatori × forward + backward = 6 esperimenti
        FeatureSelection featureSelection = new FeatureSelection();
        List<ExperimentResult> allResults = new ArrayList<>();

        logger.info("--- Feature Selection Experiments (Forward) ---");
        allResults.addAll(featureSelection.runAllExperiments(trainSet, 5, 10, 42, false));

        logger.info("--- Feature Selection Experiments (Backward) ---");
        allResults.addAll(featureSelection.runAllExperiments(trainSet, 5, 10, 42, true));

        // 5. Salva tutti i risultati su CSV
        saveResultsToCsv(allResults, "src/main/java/file/FeatureSelectionResults.csv");

        logger.info("=== MLWeka Pipeline completata ===");
    }

    private void saveResultsToCsv(List<ExperimentResult> results, String path) throws IOException {
        File file = new File(path);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Classifier,SearchDirection,InnerFolds,OuterFolds,AUC,Precision,Recall,F1");
            writer.newLine();

            for (ExperimentResult r : results) {
                writer.write(String.format("%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f",
                        r.getClassifierName(),
                        r.getSearchDirection(),
                        r.getInnerFolds(),
                        r.getOuterFolds(),
                        r.getEvaluation().weightedAreaUnderROC(),
                        r.getEvaluation().weightedPrecision(),
                        r.getEvaluation().weightedRecall(),
                        r.getEvaluation().weightedFMeasure()
                ));
                writer.newLine();
            }
        }
        logger.info("Risultati salvati in: " + path);
    }
}