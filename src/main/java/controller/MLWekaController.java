package controller;

import MLWeka.DataEngeneering;
import MLWeka.WekaConfig;
import weka.core.Instances;

import java.util.logging.Logger;

public class MLWekaController {

    private static final Logger logger = Logger.getLogger(MLWekaController.class.getName());
    private static final String DATASET_PATH = "src/main/java/file/DatasetClassiRelease.csv";
    private static final String CSV_PATH_NORMALIZED = "src/main/java/file/DatasetClassiRealeseNormalized.csv";

    private final DataEngeneering dataEng = new DataEngeneering();


    public void normalizeDataset() throws Exception {
        logger.info("=== Avvio Normalizzazione Z-Score ===");
        Instances data = WekaConfig.loadDataset(DATASET_PATH,false);
        Instances normalized = dataEng.normalizeMinMax(data);
        WekaConfig.saveToCsv(normalized, CSV_PATH_NORMALIZED);
        logger.info("=== Normalizzazione completata ===");
    }




}