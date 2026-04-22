package MLWeka;

import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.WrapperSubsetEval;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.AttributeSelectedClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Performs feature selection experiments using Weka's WrapperSubsetEval + GreedyStepwise
 * inside an AttributeSelectedClassifier, evaluated via outer cross-validation.
 *
 * Six total experiments are run: each of the three base classifiers (RandomForest, NaiveBayes, IBk)
 * is evaluated both without and with feature selection.
 */
public class FeatureSelection {

    private static final Logger logger = Logger.getLogger(FeatureSelection.class.getName());

    /**
     * Holds the results of a single experiment run.
     */
    public static class ExperimentResult {
        /* Name of the base classifier (e.g. "RandomForest") */
        private final String classifierName;
        /* Whether feature selection was applied */
        private final boolean withFeatureSelection;
        /* "forward" or "backward", relevant only when withFeatureSelection is true */
        private final String searchDirection;
        /* Number of inner folds used by WrapperSubsetEval */
        private final int innerFolds;
        /* Number of outer folds used for the final cross-validation */
        private final int outerFolds;
        /* Weka Evaluation object containing all computed metrics */
        private final Evaluation evaluation;

        public ExperimentResult(String classifierName,
                                boolean withFeatureSelection,
                                String searchDirection,
                                int innerFolds,
                                int outerFolds,
                                Evaluation evaluation) {
            this.classifierName = classifierName;
            this.withFeatureSelection = withFeatureSelection;
            this.searchDirection = searchDirection;
            this.innerFolds = innerFolds;
            this.outerFolds = outerFolds;
            this.evaluation = evaluation;
        }

        public String getClassifierName() { return classifierName; }
        public boolean isWithFeatureSelection() { return withFeatureSelection; }
        public String getSearchDirection() { return searchDirection; }
        public int getInnerFolds() { return innerFolds; }
        public int getOuterFolds() { return outerFolds; }
        public Evaluation getEvaluation() { return evaluation; }
    }

    /* Names of non-predictive columns that must be removed before training */
    private static final String[] NON_PREDICTIVE_COLUMNS = {"version", "path"};

    /**
     * Removes non-predictive string/identifier attributes from the dataset.
     * The class attribute (last column, "buggy") is preserved.
     *
     * @param raw the original Instances loaded from the CSV/ARFF
     * @return a new Instances object without the non-predictive columns
     * @throws Exception if the Weka Remove filter fails
     */
    private Instances removeNonPredictiveAttributes(Instances raw) throws Exception {
        // Collect 1-based indices of attributes to remove
        List<Integer> indicesToRemove = new ArrayList<>();

        for (String colName : NON_PREDICTIVE_COLUMNS) {
            int idx = raw.attribute(colName) != null ? raw.attribute(colName).index() : -1;
            if (idx >= 0) {
                // Weka Remove filter uses 1-based indices
                indicesToRemove.add(idx + 1);
            }
        }

        if (indicesToRemove.isEmpty()) {
            return raw;
        }

        String indicesStr = indicesToRemove.stream()
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        Remove remove = new Remove();
        remove.setAttributeIndices(indicesStr);
        remove.setInputFormat(raw);
        Instances filtered = Filter.useFilter(raw, remove);

        // Re-set class to last attribute ("buggy")
        filtered.setClassIndex(filtered.numAttributes() - 1);
        return filtered;
    }

    /**
     * Runs a single experiment: evaluates the given classifier (with or without feature selection)
     * via outer k-fold cross-validation.
     *
     * When withFeatureSelection is true, the classifier is wrapped inside an
     * AttributeSelectedClassifier so that feature selection is re-done on each training fold,
     * avoiding data leakage.
     *
     * @param dataset              the pre-processed Instances (non-predictive columns already removed)
     * @param baseClassifier       the base Weka classifier to use
     * @param innerFolds           number of folds for WrapperSubsetEval (used only when withFS=true)
     * @param outerFolds           number of folds for the outer cross-validation
     * @param seed                 random seed for reproducibility
     * @param backward             true = backward search, false = forward search
     * @param withFeatureSelection whether to wrap the classifier with feature selection
     * @return an ExperimentResult containing all metadata and the Evaluation object
     * @throws Exception if Weka throws during setup or evaluation
     */
    public ExperimentResult runExperiment(Instances dataset,
                                          Classifier baseClassifier,
                                          int innerFolds,
                                          int outerFolds,
                                          int seed,
                                          boolean backward,
                                          boolean withFeatureSelection) throws Exception {

        String classifierName = baseClassifier.getClass().getSimpleName();
        String searchDirection = backward ? "backward" : "forward";

        Classifier classifierToEvaluate;

        if (withFeatureSelection) {
            // --- WrapperSubsetEval: evaluates feature subsets using the base classifier ---
            WrapperSubsetEval evaluator = new WrapperSubsetEval();
            evaluator.setClassifier(baseClassifier);
            // Inner folds are used only to score candidate feature subsets, not for final evaluation
            evaluator.setFolds(innerFolds);
            evaluator.setSeed(seed);

            // --- GreedyStepwise search direction ---
            GreedyStepwise search = new GreedyStepwise();
            search.setSearchBackwards(backward);

            // --- AttributeSelectedClassifier: ties evaluator + search + base classifier together ---
            // Feature selection will be re-executed on every outer training fold automatically
            AttributeSelectedClassifier asc = new AttributeSelectedClassifier();
            asc.setEvaluator(evaluator);
            asc.setSearch(search);
            asc.setClassifier(baseClassifier);

            classifierToEvaluate = asc;
        } else {
            // Plain base classifier, no feature selection
            classifierToEvaluate = baseClassifier;
        }

        // --- Outer cross-validation ---
        // For each outer fold, Weka trains on the training split (triggering FS if wrapped)
        // and evaluates on the held-out fold.
        Evaluation evaluation = new Evaluation(dataset);
        evaluation.crossValidateModel(classifierToEvaluate, dataset, outerFolds, new Random(seed));

        logger.info(String.format(
                "Completed: %s | FS=%b | search=%s | innerFolds=%d | outerFolds=%d | AUC=%.4f | Precision=%.4f | Recall=%.4f",
                classifierName,
                withFeatureSelection,
                withFeatureSelection ? searchDirection : "n/a",
                innerFolds,
                outerFolds,
                evaluation.weightedAreaUnderROC(),
                evaluation.weightedPrecision(),
                evaluation.weightedRecall()
        ));

        return new ExperimentResult(
                classifierName,
                withFeatureSelection,
                withFeatureSelection ? searchDirection : "n/a",
                innerFolds,
                outerFolds,
                evaluation
        );
    }

    /**
     * Entry point: runs all six experiments for the three base classifiers,
     * each with and without WrapperSubsetEval + GreedyStepwise feature selection.
     *
     * @param rawDataset  the full dataset loaded from file (with "buggy" as last column)
     * @param innerFolds  inner fold count for WrapperSubsetEval
     * @param outerFolds  outer fold count for the final cross-validation
     * @param seed        random seed
     * @param backward    true = backward search, false = forward search
     * @return list of six ExperimentResult objects, one per (classifier × FS mode) combination
     * @throws Exception if any Weka operation fails
     */
    public List<ExperimentResult> runAllExperiments(Instances rawDataset,
                                                    int innerFolds,
                                                    int outerFolds,
                                                    int seed,
                                                    boolean backward) throws Exception {

        // Step 1: remove non-predictive columns and set class index to last attribute ("buggy")
        Instances dataset = removeNonPredictiveAttributes(rawDataset);
        dataset.setClassIndex(dataset.numAttributes() - 1);

        // Step 2: define the three base classifiers
        List<Classifier> baseClassifiers = List.of(
                new RandomForest(),
                new NaiveBayes(),
                new IBk()
        );

        List<ExperimentResult> results = new ArrayList<>();

        for (Classifier baseClassifier : baseClassifiers) {
            ExperimentResult result = runExperiment(
                    dataset, baseClassifier, innerFolds, outerFolds, seed, backward, true);
            results.add(result);
        }

        return results;
    }
}