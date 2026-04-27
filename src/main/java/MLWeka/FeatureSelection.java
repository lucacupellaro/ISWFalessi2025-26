package MLWeka;

import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.WrapperSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.logging.Logger;

public class FeatureSelection {

    private static final Logger logger = Logger.getLogger(FeatureSelection.class.getName());
    private static final String[] NON_PREDICTIVE_COLUMNS = {"version", "path"};

    /**
     * Tipi di classifier utilizzabili nel wrapper di feature selection.
     * Ogni enum sa istanziare il proprio Classifier Weka ed esporre un nome leggibile.
     */
    public enum ClassifierType {
        NAIVE_BAYES("NaiveBayes") {
            @Override
            public Classifier newInstance() {
                return new NaiveBayes();
            }
        },
        RANDOM_FOREST("RandomForest") {
            @Override
            public Classifier newInstance() {
                return new RandomForest();
            }
        },
        IBK("IBk") {
            @Override
            public Classifier newInstance() {
                return new IBk();
            }
        };

        private final String displayName;

        ClassifierType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public abstract Classifier newInstance();
    }

    private Instances removeNonPredictiveAttributes(Instances raw) throws Exception {
        StringBuilder indices = new StringBuilder();
        for (String colName : NON_PREDICTIVE_COLUMNS) {
            if (raw.attribute(colName) != null) {
                if (indices.length() > 0) indices.append(",");
                indices.append(raw.attribute(colName).index() + 1);
            }
        }
        if (indices.length() == 0) return raw;

        Remove remove = new Remove();
        remove.setAttributeIndices(indices.toString());
        remove.setInputFormat(raw);
        Instances filtered = Filter.useFilter(raw, remove);
        filtered.setClassIndex(filtered.numAttributes() - 1);
        return filtered;
    }

    public void runBackwardSearch(Instances rawDataset, ClassifierType classifierType,
                                  int wrapperFolds, int wrapperSeed,
                                  int outerFolds, int outerSeed,
                                  String inputCsvPath, String outputPath) throws Exception {
        runSearch(rawDataset, true, classifierType, wrapperFolds, wrapperSeed,
                outerFolds, outerSeed, inputCsvPath, outputPath);
    }

    public void runForwardSearch(Instances rawDataset, ClassifierType classifierType,
                                 int wrapperFolds, int wrapperSeed,
                                 int outerFolds, int outerSeed,
                                 String inputCsvPath, String outputPath) throws Exception {
        runSearch(rawDataset, false, classifierType, wrapperFolds, wrapperSeed,
                outerFolds, outerSeed, inputCsvPath, outputPath);
    }

    private void runSearch(Instances rawDataset, boolean backward,
                           ClassifierType classifierType,
                           int wrapperFolds, int wrapperSeed,
                           int outerFolds, int outerSeed,
                           String inputCsvPath, String outputPath) throws Exception {

        Instances dataset = removeNonPredictiveAttributes(rawDataset);
        dataset.setClassIndex(dataset.numAttributes() - 1);

        String direction = backward ? "Backward" : "Forward";
        logger.info("=== " + direction + " Search Feature Selection con " + classifierType.getDisplayName() + " ===");

        // Configura Wrapper con il classifier richiesto
        WrapperSubsetEval evaluator = new WrapperSubsetEval();
        evaluator.setClassifier(classifierType.newInstance());
        evaluator.setFolds(wrapperFolds);
        evaluator.setSeed(wrapperSeed);

        // Configura GreedyStepwise nella direzione richiesta
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(backward);

        // --- Esecuzione sull'intero training set ---
        AttributeSelection attSelectionFull = new AttributeSelection();
        attSelectionFull.setEvaluator(evaluator);
        attSelectionFull.setSearch(search);
        attSelectionFull.SelectAttributes(dataset);

        int[] selectedIndices = attSelectionFull.selectedAttributes();
        int predictiveCount = selectedIndices.length - 1; // escludi la classe

        logger.info("Feature selezionate: " + predictiveCount + " su " + (dataset.numAttributes() - 1));

        // --- Cross-validation per le percentuali per attributo ---
        // Ricrea evaluator e search (non riusabili dopo SelectAttributes)
        WrapperSubsetEval evaluatorCv = new WrapperSubsetEval();
        evaluatorCv.setClassifier(classifierType.newInstance());
        evaluatorCv.setFolds(wrapperFolds);
        evaluatorCv.setSeed(wrapperSeed);

        GreedyStepwise searchCv = new GreedyStepwise();
        searchCv.setSearchBackwards(backward);

        AttributeSelection attSelectionCv = new AttributeSelection();
        attSelectionCv.setEvaluator(evaluatorCv);
        attSelectionCv.setSearch(searchCv);
        // setXval(true) abilita la cross-validation interna a SelectAttributes;
        // folds e seed vengono letti dai setter prima della chiamata
        attSelectionCv.setXval(true);
        attSelectionCv.setFolds(outerFolds);
        attSelectionCv.setSeed(outerSeed);
        // SelectAttributes esegue sia la full selection che la CV quando setXval(true)
        attSelectionCv.SelectAttributes(dataset);

        // Scrivi il report
        File file = new File(outputPath);
        file.getParentFile().mkdirs();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {

            // Header
            writer.write("Feature Selection Report");
            writer.newLine();
            writer.write("========================");
            writer.newLine();
            writer.write("Input CSV: " + inputCsvPath);
            writer.newLine();
            writer.write("Search method: GreedyStepwise " + direction);
            writer.newLine();
            writer.write("Attribute evaluator: WrapperSubsetEval");
            writer.newLine();
            writer.write("Wrapper classifier: " + classifierType.getDisplayName());
            writer.newLine();
            writer.write("Wrapper internal folds: " + wrapperFolds);
            writer.newLine();
            writer.write("Wrapper internal seed: " + wrapperSeed);
            writer.newLine();
            writer.write("Outer cross-validation folds: " + outerFolds);
            writer.newLine();
            writer.write("Outer cross-validation seed: " + outerSeed);
            writer.newLine();
            writer.write("Class attribute: " + dataset.classAttribute().name().toUpperCase());
            writer.newLine();

            // Attributi selezionati sull'intero training set
            writer.newLine();
            writer.write("Selected attributes on full training set");
            writer.newLine();
            writer.write("---------------------------------------");
            writer.newLine();
            writer.write("Selected predictive attributes: " + predictiveCount);
            writer.newLine();
            for (int idx : selectedIndices) {
                if (idx != dataset.classIndex()) {
                    writer.write("- [" + (idx + 1) + "] " + dataset.attribute(idx).name());
                    writer.newLine();
                }
            }

            // Blocco grezzo full-selection
            writer.newLine();
            writer.write("Weka full-selection output");
            writer.newLine();
            writer.write("--------------------------");
            writer.newLine();
            writer.write(attSelectionFull.toResultsString());
            writer.newLine();

            // Blocco cross-validation con percentuali per attributo
            writer.write("Weka " + outerFolds + "-fold cross-validation output");
            writer.newLine();
            writer.write("------------------------------------");
            writer.newLine();
            writer.write(attSelectionCv.CVResultsString());
            writer.newLine();
        }

        logger.info("Risultati salvati in: " + outputPath);
    }
}