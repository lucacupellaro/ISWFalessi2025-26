package mlweka;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Genera la tabella:
 *
 * Variable | Mean A | Mean B | Mean C | Correlation with NSmells | Correlation with Defectiveness
 *
 * Regole:
 * - Le medie sono calcolate su A, B e C.
 * - Le correlazioni sono calcolate SOLO su A.
 * - Defectiveness = buggy convertito in binario: yes = 1, no = 0.
 * - Correlazione usata: Spearman rho.
 * - Asterisco se p-value < 0.05.
 */
public class CorrelationFeatures {

    private static final Logger LOGGER = Logger.getLogger(CorrelationFeatures.class.getName());

    private static final String DATASET_A_PATH =
            "src/main/java/file/MIlestone3/A_original_complete_NN.csv";

    private static final String DATASET_B_PATH =
            "src/main/java/file/MIlestone3/B_counterfactual_no_smells_NN.csv";

    private static final String DATASET_C_PATH =
            "src/main/java/file/MIlestone3/C_original_no_smells_NN.csv";

    private static final String OUTPUT_PATH =
            "src/main/java/file/MIlestone3/correlazioni.csv";

    private static final String NSMELLS_ATTRIBUTE = "nSmells";
    private static final String BUGGY_ATTRIBUTE = "buggy";

    private static final double ALPHA = 0.05;

    private CorrelationFeatures() {
    }

    public static void main(String[] args) throws Exception {
        Instances datasetA = WekaConfig.loadDataset(DATASET_A_PATH, true);
        Instances datasetB = WekaConfig.loadDataset(DATASET_B_PATH, true);
        Instances datasetC = WekaConfig.loadDataset(DATASET_C_PATH, true);

        validateDataset(datasetA, "A");
        validateDataset(datasetB, "B");
        validateDataset(datasetC, "C");

        List<TableRow> rows = buildTable(datasetA, datasetB, datasetC);

        saveTableToCsv(rows, OUTPUT_PATH);
        printTable(rows);

        LOGGER.info("");
        LOGGER.info("File creato: " + new File(OUTPUT_PATH).getAbsolutePath());
    }

    private static void validateDataset(Instances data, String datasetName) {
        if (data.attribute(NSMELLS_ATTRIBUTE) == null) {
            throw new IllegalArgumentException("Nel dataset " + datasetName + " manca la colonna: " + NSMELLS_ATTRIBUTE);
        }

        if (data.classIndex() < 0) {
            throw new IllegalArgumentException("Nel dataset " + datasetName + " non è stata impostata la classe.");
        }

        if (!data.classAttribute().name().equals(BUGGY_ATTRIBUTE)) {
            throw new IllegalArgumentException(
                    "Nel dataset " + datasetName + " la classe attesa è '" + BUGGY_ATTRIBUTE +
                            "', ma ho trovato: " + data.classAttribute().name()
            );
        }
    }

    private static List<TableRow> buildTable(
            Instances datasetA,
            Instances datasetB,
            Instances datasetC
    ) {
        List<TableRow> rows = new ArrayList<>();

        for (int i = 0; i < datasetA.numAttributes(); i++) {
            Attribute attributeA = datasetA.attribute(i);

            if (i == datasetA.classIndex() || !attributeA.isNumeric()) {
                // skip class attribute and non-numeric attributes
            } else {
                TableRow row = buildRowForAttribute(attributeA, datasetA, datasetB, datasetC);
                if (row != null) {
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    private static TableRow buildRowForAttribute(Attribute attributeA,
                                                     Instances datasetA,
                                                     Instances datasetB,
                                                     Instances datasetC) {
        String featureName = attributeA.name();

        Attribute attributeB = datasetB.attribute(featureName);
        Attribute attributeC = datasetC.attribute(featureName);

        if (attributeB == null || attributeC == null) {
            LOGGER.info(String.format("Feature saltata perché non presente in tutti i dataset: %s", featureName));
            return null;
        }

        if (!attributeB.isNumeric() || !attributeC.isNumeric()) {
            LOGGER.info(String.format("Feature saltata perché non numerica in tutti i dataset: %s", featureName));
            return null;
        }

        double meanA = mean(datasetA, featureName);
        double meanB = mean(datasetB, featureName);
        double meanC = mean(datasetC, featureName);

        String correlationWithNSmells;

        if (featureName.equals(NSMELLS_ATTRIBUTE)) {
            correlationWithNSmells = "-";
        } else {
            CorrelationResult resultNSmells = spearmanCorrelation(
                    datasetA,
                    featureName,
                    NSMELLS_ATTRIBUTE
            );

            correlationWithNSmells = formatCorrelation(resultNSmells);
        }

        CorrelationResult resultDefectiveness = spearmanCorrelationWithDefectiveness(
                datasetA,
                featureName
        );

        String correlationWithDefectiveness = formatCorrelation(resultDefectiveness);

        return new TableRow(
                featureName,
                formatMean(meanA),
                formatMean(meanB),
                formatMean(meanC),
                correlationWithNSmells,
                correlationWithDefectiveness
        );
    }

    private static double mean(Instances data, String attributeName) {
        Attribute attribute = data.attribute(attributeName);

        if (attribute == null) {
            return Double.NaN;
        }

        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);

            if (!instance.isMissing(attribute)) {
                sum += instance.value(attribute);
                count++;
            }
        }

        if (count == 0) {
            return Double.NaN;
        }

        return sum / count;
    }

    /**
     * Correlazione Spearman tra due feature numeriche del dataset A.
     *
     * Esempio:
     * rho(loc_A, nSmells_A)
     * rho(churn_A, nSmells_A)
     */
    private static CorrelationResult spearmanCorrelation(
            Instances data,
            String featureName,
            String targetName
    ) {
        Attribute feature = data.attribute(featureName);
        Attribute target = data.attribute(targetName);

        if (feature == null || target == null) {
            return CorrelationResult.nan();
        }

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);

            if (!instance.isMissing(feature) && !instance.isMissing(target)) {
                x.add(instance.value(feature));
                y.add(instance.value(target));
            }
        }

        return computeSpearman(x, y);
    }

    /**
     * Correlazione Spearman tra una feature numerica e la defectiveness.

     */
    private static CorrelationResult spearmanCorrelationWithDefectiveness(
            Instances data,
            String featureName
    ) {
        Attribute feature = data.attribute(featureName);
        Attribute buggy = data.classAttribute();

        if (feature == null || buggy == null) {
            return CorrelationResult.nan();
        }

        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();

        for (int i = 0; i < data.numInstances(); i++) {
            addDefectivenessDataPoint(data.instance(i), feature, buggy, x, y);
        }

        return computeSpearman(x, y);
    }

    private static void addDefectivenessDataPoint(Instance instance,
                                                      Attribute feature,
                                                      Attribute buggy,
                                                      List<Double> x,
                                                      List<Double> y) {
        if (instance.isMissing(feature) || instance.isMissing(buggy)) {
            return;
        }

        Double buggyBinary = buggyToBinary(instance, buggy);

        if (buggyBinary != null) {
            x.add(instance.value(feature));
            y.add(buggyBinary);
        }
    }

    private static Double buggyToBinary(Instance instance, Attribute buggyAttribute) {
        if (buggyAttribute.isNominal() || buggyAttribute.isString()) {
            String value = instance.stringValue(buggyAttribute).trim().toLowerCase(Locale.ROOT);

            if (value.equals("yes") || value.equals("true") || value.equals("1") || value.equals(BUGGY_ATTRIBUTE)) {
                return 1.0;
            }

            if (value.equals("no") || value.equals("false") || value.equals("0") || value.equals("clean")) {
                return 0.0;
            }

            return null;
        }

        if (buggyAttribute.isNumeric()) {
            double value = instance.value(buggyAttribute);

            if (value == 1.0) {
                return 1.0;
            }

            if (value == 0.0) {
                return 0.0;
            }
        }

        return null;
    }

    private static CorrelationResult computeSpearman(List<Double> x, List<Double> y) {
        if (x.size() != y.size()) {
            throw new IllegalArgumentException("I due vettori devono avere la stessa dimensione.");
        }

        int n = x.size();

        if (n < 3) {
            return CorrelationResult.nan();
        }

        double[] xArray = toArray(x);
        double[] yArray = toArray(y);

        double[] rankX = rank(xArray);
        double[] rankY = rank(yArray);

        double rho = pearson(rankX, rankY);
        double pValue = pValueFromCorrelation(rho, n);

        return new CorrelationResult(rho, pValue, n);
    }

    /**
     * Ranking con gestione dei pari tramite average rank.
     *
     * Esempio:
     * valori: 10, 20, 20, 40
     * rank:   1,  2.5, 2.5, 4
     */
    private static double[] rank(double[] values) {
        int n = values.length;

        Integer[] indexes = new Integer[n];

        for (int i = 0; i < n; i++) {
            indexes[i] = i;
        }

        Arrays.sort(indexes, Comparator.comparingDouble(i -> values[i]));

        double[] ranks = new double[n];

        int i = 0;

        while (i < n) {
            int j = i;

            while (j + 1 < n && Double.compare(values[indexes[j]], values[indexes[j + 1]]) == 0) {
                j++;
            }

            double averageRank = ((i + 1) + (j + 1)) / 2.0;

            for (int k = i; k <= j; k++) {
                ranks[indexes[k]] = averageRank;
            }

            i = j + 1;
        }

        return ranks;
    }

    private static double pearson(double[] x, double[] y) {
        int n = x.length;

        double meanX = 0.0;
        double meanY = 0.0;

        for (int i = 0; i < n; i++) {
            meanX += x[i];
            meanY += y[i];
        }

        meanX /= n;
        meanY /= n;

        double numerator = 0.0;
        double denominatorX = 0.0;
        double denominatorY = 0.0;

        for (int i = 0; i < n; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;

            numerator += dx * dy;
            denominatorX += dx * dx;
            denominatorY += dy * dy;
        }

        double denominator = Math.sqrt(denominatorX * denominatorY);

        if (denominator == 0.0) {
            return Double.NaN;
        }

        return numerator / denominator;
    }

    /**
     * p-value a due code per una correlazione.
     *
     * Si usa l'approssimazione:
     *
     * t = r * sqrt((n - 2) / (1 - r^2))
     *
     * con gradi di libertà n - 2.
     */
    private static double pValueFromCorrelation(double r, int n) {
        if (!Double.isFinite(r) || n < 3) {
            return Double.NaN;
        }

        if (Math.abs(r) >= 1.0) {
            return 0.0;
        }

        double t = Math.abs(r) * Math.sqrt((n - 2.0) / (1.0 - r * r));
        int degreesOfFreedom = n - 2;

        double cdf = studentTCdf(t, degreesOfFreedom);

        return 2.0 * (1.0 - cdf);
    }

    private static double studentTCdf(double t, int v) {
        if (v <= 0) {
            return Double.NaN;
        }

        if (t == 0.0) {
            return 0.5;
        }

        double x = v / (v + t * t);
        double a = v / 2.0;
        double b = 0.5;

        double ib = regularizedBeta(x, a, b);

        if (t > 0) {
            return 1.0 - 0.5 * ib;
        } else {
            return 0.5 * ib;
        }
    }

    private static double regularizedBeta(double x, double a, double b) {
        if (x < 0.0 || x > 1.0) {
            return Double.NaN;
        }

        if (x == 0.0) {
            return 0.0;
        }

        if (x == 1.0) {
            return 1.0;
        }

        double bt = Math.exp(
                logGamma(a + b)
                        - logGamma(a)
                        - logGamma(b)
                        + a * Math.log(x)
                        + b * Math.log(1.0 - x)
        );

        if (x < (a + 1.0) / (a + b + 2.0)) {
            return bt * betaContinuedFraction(x, a, b) / a;
        } else {
            // Identity: I_x(a,b) = 1 - I_{1-x}(b,a) — swap a and b intentionally
            double swappedA = b;
            double swappedB = a;
            return 1.0 - bt * betaContinuedFraction(1.0 - x, swappedA, swappedB) / b;
        }
    }

    private static double betaContinuedFraction(double x, double a, double b) {
        final int maxIterations = 10_000;
        final double epsilon = 3.0e-14;
        final double fpMin = 1.0e-300;

        double qab = a + b;
        double qap = a + 1.0;
        double qam = a - 1.0;

        double c = 1.0;
        double d = 1.0 - qab * x / qap;

        if (Math.abs(d) < fpMin) {
            d = fpMin;
        }

        d = 1.0 / d;
        double h = d;

        for (int m = 1; m <= maxIterations; m++) {
            int m2 = 2 * m;

            double aa = m * (b - m) * x / ((qam + m2) * (a + m2));
            d = 1.0 + aa * d;

            if (Math.abs(d) < fpMin) {
                d = fpMin;
            }

            c = 1.0 + aa / c;

            if (Math.abs(c) < fpMin) {
                c = fpMin;
            }

            d = 1.0 / d;
            h *= d * c;

            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2));
            d = 1.0 + aa * d;

            if (Math.abs(d) < fpMin) {
                d = fpMin;
            }

            c = 1.0 + aa / c;

            if (Math.abs(c) < fpMin) {
                c = fpMin;
            }

            d = 1.0 / d;
            double del = d * c;
            h *= del;

            if (Math.abs(del - 1.0) < epsilon) {
                break;
            }
        }

        return h;
    }

    private static double logGamma(double x) {
        double[] coefficients = {
                676.5203681218851,
                -1259.1392167224028,
                771.32342877765313,
                -176.61502916214059,
                12.507343278686905,
                -0.13857109526572012,
                9.9843695780195716e-6,
                1.5056327351493116e-7
        };

        if (x < 0.5) {
            return Math.log(Math.PI) - Math.log(Math.sin(Math.PI * x)) - logGamma(1.0 - x);
        }

        x -= 1.0;

        double a = 0.99999999999980993;

        for (int i = 0; i < coefficients.length; i++) {
            a += coefficients[i] / (x + i + 1.0);
        }

        double t = x + coefficients.length - 0.5;

        return 0.5 * Math.log(2.0 * Math.PI) + (x + 0.5) * Math.log(t) - t + Math.log(a);
    }

    private static double[] toArray(List<Double> values) {
        double[] array = new double[values.size()];

        for (int i = 0; i < values.size(); i++) {
            array[i] = values.get(i);
        }

        return array;
    }

    private static String formatMean(double value) {
        if (!Double.isFinite(value)) {
            return "NA";
        }

        return String.format(Locale.US, "%.4f", value);
    }

    private static String formatCorrelation(CorrelationResult result) {
        if (!Double.isFinite(result.correlation)) {
            return "NA";
        }

        String value = String.format(Locale.US, "%.3f", result.correlation);

        if (Double.isFinite(result.pValue) && result.pValue < ALPHA) {
            value += "*";
        }

        return value;
    }

    private static void saveTableToCsv(List<TableRow> rows, String outputPath) throws IOException {
        File file = new File(outputPath);

        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Variable,Mean A,Mean B,Mean C,Correlation with NSmells,Correlation with Defectiveness");
            writer.newLine();

            for (TableRow row : rows) {
                writer.write(String.join(",",
                        escapeCsv(row.variable),
                        row.meanA,
                        row.meanB,
                        row.meanC,
                        escapeCsv(row.correlationWithNSmells),
                        escapeCsv(row.correlationWithDefectiveness)
                ));
                writer.newLine();
            }
        }
    }

    private static void printTable(List<TableRow> rows) {
        if (!LOGGER.isLoggable(java.util.logging.Level.INFO)) {
            return;
        }
        LOGGER.info(String.format(
                "%-25s %12s %12s %12s %25s %32s",
                "Variable",
                "Mean A",
                "Mean B",
                "Mean C",
                "Corr NSmells",
                "Corr Defectiveness"
        ));

        LOGGER.info("-".repeat(125));

        for (TableRow row : rows) {
            LOGGER.info(String.format(
                    "%-25s %12s %12s %12s %25s %32s",
                    row.variable,
                    row.meanA,
                    row.meanB,
                    row.meanC,
                    row.correlationWithNSmells,
                    row.correlationWithDefectiveness
            ));
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    private static class TableRow {
        private final String variable;
        private final String meanA;
        private final String meanB;
        private final String meanC;
        private final String correlationWithNSmells;
        private final String correlationWithDefectiveness;

        private TableRow(
                String variable,
                String meanA,
                String meanB,
                String meanC,
                String correlationWithNSmells,
                String correlationWithDefectiveness
        ) {
            this.variable = variable;
            this.meanA = meanA;
            this.meanB = meanB;
            this.meanC = meanC;
            this.correlationWithNSmells = correlationWithNSmells;
            this.correlationWithDefectiveness = correlationWithDefectiveness;
        }
    }

    private static class CorrelationResult {
        private final double correlation;
        private final double pValue;
        private final int sampleSize;

        private CorrelationResult(double correlation, double pValue, int sampleSize) {
            this.correlation = correlation;
            this.pValue = pValue;
            this.sampleSize = sampleSize;
        }

        private static CorrelationResult nan() {
            return new CorrelationResult(Double.NaN, Double.NaN, 0);
        }
    }
}