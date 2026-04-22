package MLWeka;

import weka.core.Instances;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class WekaConfig {

    private static final Logger logger = Logger.getLogger(WekaConfig.class.getName());

    private WekaConfig() {
    }

    /**
     * Carica il CSV come Instances di Weka, rimuove le colonne non-feature
     * (release e className) e imposta "buggy" come classe.
     */
    public static Instances loadDataset(String csvPath) throws IOException {
        CSVLoader loader = new CSVLoader();
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();

        // Rimuovi className (indice 1) e release (indice 0) — rimuovi prima l'indice più alto
        data.deleteAttributeAt(1);
        data.deleteAttributeAt(0);

        // Imposta l'ultima colonna (buggy) come classe
        data.setClassIndex(data.numAttributes() - 1);

        logger.info("Dataset caricato: " + data.numInstances() + " istanze, "
                + data.numAttributes() + " attributi (classe: " + data.classAttribute().name() + ")");

        return data;
    }
}