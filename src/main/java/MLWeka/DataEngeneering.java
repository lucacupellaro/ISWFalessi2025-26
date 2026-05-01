package MLWeka;

import weka.core.Instances;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Logger;

public class DataEngeneering {

    private static final Logger logger = Logger.getLogger(DataEngeneering.class.getName());

    /*
     * Normalizza le feature numeriche applicando log1p: x -> log(1 + x).
     * Ignora l'attributo classe (buggy).
     *
    public Instances normalizeLog1p(Instances data) {
        Instances normalized = new Instances(data);
        int classIndex = normalized.classIndex();

        for (int j = 0; j < normalized.numAttributes(); j++) {
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
    */

    /*
     * Standardizza le feature numeriche con Z-score: x -> (x - media) / stddev.
     * Se la deviazione standard è zero (feature costante, es. tutti zeri),
     * la colonna viene lasciata invariata per evitare divisioni per zero.
     * Ignora l'attributo classe (buggy).
     *
    public Instances standardizeZScore(Instances data) {
        Instances standardized = new Instances(data);
        int classIndex = standardized.classIndex();
        int n = standardized.numInstances();

        for (int j = 0; j < standardized.numAttributes(); j++) {
            if (j == classIndex || !standardized.attribute(j).isNumeric()) {
                continue;
            }

            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += standardized.instance(i).value(j);
            }
            double mean = sum / n;

            double variance = 0.0;
            for (int i = 0; i < n; i++) {
                double diff = standardized.instance(i).value(j) - mean;
                variance += diff * diff;
            }
            double stddev = Math.sqrt(variance / n);

            if (stddev == 0.0) {
                continue;
            }

            for (int i = 0; i < n; i++) {
                double val = standardized.instance(i).value(j);
                standardized.instance(i).setValue(j, (val - mean) / stddev);
            }
        }

        logger.info("Standardizzazione Z-score applicata su " +
                (standardized.numAttributes() - 1) + " feature numeriche");
        return standardized;
    }
    */

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

        logger.info("Normalizzazione Min-Max applicata su " +
                (normalized.numAttributes() - 1) + " feature numeriche");
        return normalized;
    }



}