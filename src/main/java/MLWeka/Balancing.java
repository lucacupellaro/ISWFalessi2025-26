package MLWeka;

import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.Resample;

import java.util.logging.Logger;

public class Balancing {

    private static final Logger logger = Logger.getLogger(Balancing.class.getName());

    /**
     * Oversampling della classe minoritaria tramite replicazione.
     *
     * Usa weka.filters.supervised.instance.Resample configurato come segue:
     *  - biasToUniformClass = 1.0  -> distribuzione uniforme tra le classi
     *  - noReplacement = false     -> campionamento con replacement (replicazione)
     *  - sampleSizePercent = 100.0 -> dimensione dell'output = dimensione originale
     *  - randomSeed = seed passato dall'esterno (default 1)
     *
     * Con biasToUniformClass=1.0 e noReplacement=false, la classe minoritaria viene
     * replicata fino a pareggiare la maggioritaria. Il modello vede quindi un numero
     * di istanze equo tra le due classi, al costo della possibile specializzazione
     * del modello sulle informazioni replicate.
     */
    public Instances oversample(Instances data, int seed) throws Exception {
        int beforeSize = data.numInstances();
        int[] beforeCounts = classCounts(data);

        Resample resample = new Resample();
        resample.setBiasToUniformClass(1.0);
        resample.setNoReplacement(false);
        resample.setSampleSizePercent(100.0);
        resample.setRandomSeed(seed);
        resample.setInputFormat(data);

        Instances balanced = Filter.useFilter(data, resample);

        int afterSize = balanced.numInstances();
        int[] afterCounts = classCounts(balanced);

        logger.info("Oversampling completato:");
        logger.info("  Prima:  " + beforeSize + " istanze  " + formatCounts(data, beforeCounts));
        logger.info("  Dopo:   " + afterSize + " istanze  " + formatCounts(balanced, afterCounts));

        return balanced;
    }

    private int[] classCounts(Instances data) {
        int[] counts = new int[data.numClasses()];
        for (int i = 0; i < data.numInstances(); i++) {
            counts[(int) data.instance(i).classValue()]++;
        }
        return counts;
    }

    private String formatCounts(Instances data, int[] counts) {
        StringBuilder sb = new StringBuilder("(");
        for (int c = 0; c < counts.length; c++) {
            sb.append(data.classAttribute().value(c)).append("=").append(counts[c]);
            if (c < counts.length - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }
}