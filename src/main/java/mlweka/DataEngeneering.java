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

        for (int j = 0; j < normalized.numAttributes(); j++) {
            if (j != classIndex && normalized.attribute(j).isNumeric()) {
                normalizeAttribute(normalized, j);
            }
        }

        logger.log(Level.INFO, "Normalizzazione Min-Max applicata su {0} feature numeriche",
                normalized.numAttributes() - 1);
        return normalized;
    }

    private void normalizeAttribute(Instances data, int attributeIndex) {
        int n = data.numInstances();
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            double val = data.instance(i).value(attributeIndex);
            if (val < min) min = val;
            if (val > max) max = val;
        }

        double range = max - min;
        if (range == 0.0) {
            return;
        }

        for (int i = 0; i < n; i++) {
            double val = data.instance(i).value(attributeIndex);
            data.instance(i).setValue(attributeIndex, (val - min) / range);
        }
    }



}