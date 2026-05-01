package mlweka;

import weka.core.Instances;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DataEngeneering {

    private static final Logger logger = Logger.getLogger(DataEngeneering.class.getName());

    /**
     * Normalizzazione Min-Max: scala ogni feature numerica in [0, 1].
     * x -> (x - min) / (max - min).
     * Se max == min la feature è costante e viene lasciata invariata.
     * Ignora l'attributo classe (buggy).
     */
    public Instances normalizeMinMax(Instances data) {
        Instances normalized = new Instances(data);
        int classIndex = normalized.classIndex();
        int n = normalized.numInstances();

        for (int j = 0; j < normalized.numAttributes(); j++) {
            if (j == classIndex || !normalized.attribute(j).isNumeric()) {
                continue;
            }

            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                double val = normalized.instance(i).value(j);
                if (val < min) min = val;
                if (val > max) max = val;
            }

            double range = max - min;
            if (range == 0.0) {
                continue;
            }

            for (int i = 0; i < n; i++) {
                double val = normalized.instance(i).value(j);
                normalized.instance(i).setValue(j, (val - min) / range);
            }
        }

        logger.log(Level.INFO, "Normalizzazione Min-Max applicata su {0} feature numeriche",
                normalized.numAttributes() - 1);
        return normalized;
    }



}