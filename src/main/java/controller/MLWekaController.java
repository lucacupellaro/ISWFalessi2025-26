package controller;

import MLWeka.Balancing;
import MLWeka.DataEngeneering;
import MLWeka.FeatureSelection;
import MLWeka.FeatureSelection.ClassifierType;
import MLWeka.WekaConfig;
import weka.core.Instances;

import java.util.logging.Logger;

public class MLWekaController {

    private static final Logger logger = Logger.getLogger(MLWekaController.class.getName());
    private static final String DATASET_PATH = "src/main/java/file/DatasetClassiRelease.csv";
    private static final String CSV_PATH_NORMALIZED = "src/main/java/file/DatasetClassiRealeseNormalized.csv";
    private static final String CSV_PATH_NORMALIZED_shuffled = "src/main/java/file/DatasetClassiRealeseNormalizedShuffled.csv";
    private static final String TRAIN_BALANCED_CSV_PATH = "src/main/java/file/DatasetTrainingBalancedFull.csv";


    private static final int WRAPPER_FOLDS = 5;
    private static final int WRAPPER_SEED = 1;
    private static final int CV_FOLDS = 10;
    private static final int CV_SEED = 1;
    private static final int SPLIT_SEED = 42;

    private final DataEngeneering dataEng = new DataEngeneering();

    private Instances[] loadAndSplit() throws Exception {
        Instances data = WekaConfig.loadDataset(DATASET_PATH);
        return dataEng.stratifiedSplit(data, SPLIT_SEED);
    }

    public void runFeatureSelection() throws Exception {
        /*
        logger.info("=== Avvio Feature Selection Pipeline ===");
        Instances data = WekaConfig.loadDataset(DATASET_PATH);

        //Instances[] split = loadAndSplit();
        Instances trainSet = dataEng.standardizeZScore(data);

        dataEng.saveToCsv(trainSet, TRAIN_CSV_PATH);

        ClassifierType classifierType = ClassifierType.IBK;
        String baseName = classifierType.name().toLowerCase();

        FeatureSelection featureSelection = new FeatureSelection();

        featureSelection.runBackwardSearch(
                trainSet, classifierType,
                WRAPPER_FOLDS, WRAPPER_SEED, CV_FOLDS, CV_SEED,
                TRAIN_CSV_PATH,
                "src/main/java/file/" + baseName + "BackwardSearch.txt");

        featureSelection.runForwardSearch(
                trainSet, classifierType,
                WRAPPER_FOLDS, WRAPPER_SEED, CV_FOLDS, CV_SEED,
                TRAIN_CSV_PATH,
                "src/main/java/file/" + baseName + "ForwardSearch.txt");

        logger.info("=== Feature Selection Pipeline completata ===");
                 */
    }

    public void normalizeDataset() throws Exception {
        logger.info("=== Avvio Normalizzazione Z-Score ===");
        Instances data = WekaConfig.loadDataset(DATASET_PATH);
        Instances normalized = dataEng.normalizeMinMax(data);
        dataEng.saveToCsv(normalized, CSV_PATH_NORMALIZED);
        logger.info("=== Normalizzazione completata ===");
    }

    public void normalizeDatasetShuffled() throws Exception {
        logger.info("=== Avvio Normalizzazione Z-Score ===");
        Instances data = WekaConfig.loadDataset(DATASET_PATH,false);
        Instances normalized = dataEng.normalizeMinMax(data);
        normalized.randomize(new java.util.Random());
        dataEng.saveToCsv(normalized, CSV_PATH_NORMALIZED_shuffled);
        logger.info("=== Normalizzazione e shuffle completati ===");
    }

    public void runBalancing() throws Exception {
        logger.info("=== Avvio Balancing Pipeline ===");

        Instances[] split = loadAndSplit();
        Instances trainSet = split[0];

        Balancing balancing = new Balancing();
        trainSet = balancing.oversample(trainSet, 1);
        dataEng.saveToCsv(trainSet, TRAIN_BALANCED_CSV_PATH);

        logger.info("=== Balancing Pipeline completata ===");
    }

}