package mlweka;



import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Seleziona le classi candidate per la Milestone 4.
 *
 * Criterio:
 * 1. Prende solo le classi dell'ultima release.
 * 2. Rimuove le classi troppo piccole e semplici.
 * 3. Ordina le classi rimanenti per:
 *      - nSmells decrescente
 *      - loc decrescente
 *      - cyclomaticComplexity decrescente
 * 4. Salva un nuovo CSV nella cartella Milestone4.
 */
public class Chooseclass {

    private static final String INPUT_PATH =
            "/home/lucacupellaro/luca/Universita/ISW2/2025_2026/falessi/ProgettoEsame/src/main/java/file/DatasetClassiRelease.csv";

    private static final String OUTPUT_PATH =
            "/home/lucacupellaro/luca/Universita/ISW2/2025_2026/falessi/ProgettoEsame/src/main/java/file/Milestone4/ClassiSelezionate.csv";

    private static final String RELEASE = "release";
    private static final String LOC = "loc";
    private static final String NSMELLS = "nSmells";
    private static final String CYCLOMATIC_COMPLEXITY = "cyclomaticComplexity";
    private static final String N_BRANCHES = "nBranches";
    private static final String MAX_NESTING_DEPTH = "maxNestingDepth";

    private Chooseclass() {
    }

    public static void main(String[] args) throws Exception {
        Instances data = WekaConfig.loadDataset(INPUT_PATH, false);

        validateRequiredAttributes(data);

        String lastRelease = findLastRelease(data);
        System.out.println("Ultima release individuata: " + lastRelease);

        List<Instance> lastReleaseInstances = filterByRelease(data, lastRelease);

        double locQ1 = computeFirstQuartile(lastReleaseInstances, data.attribute(LOC));
        System.out.println("Soglia LOC Q1 per filtro classi piccole: " + locQ1);

        List<Instance> selectedClasses = new ArrayList<>();

        for (Instance instance : lastReleaseInstances) {
            if (!isTooSmallAndSimple(instance, locQ1)) {
                selectedClasses.add(instance);
            }
        }

        selectedClasses.sort(buildRankingComparator(data));

        saveSelectedClasses(data, selectedClasses, OUTPUT_PATH);

        System.out.println("Classi ultima release: " + lastReleaseInstances.size());
        System.out.println("Classi selezionate dopo filtro: " + selectedClasses.size());
        System.out.println("File creato: " + OUTPUT_PATH);
    }

    private static void validateRequiredAttributes(Instances data) {
        List<String> required = Arrays.asList(
                RELEASE,
                LOC,
                NSMELLS,
                CYCLOMATIC_COMPLEXITY,
                N_BRANCHES,
                MAX_NESTING_DEPTH
        );

        for (String attribute : required) {
            if (data.attribute(attribute) == null) {
                throw new IllegalArgumentException("Colonna mancante nel dataset: " + attribute);
            }
        }
    }

    private static String findLastRelease(Instances data) {
        Attribute releaseAttribute = data.attribute(RELEASE);

        String lastRelease = null;

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);

            if (instance.isMissing(releaseAttribute)) {
                continue;
            }

            String release = getStringValue(instance, releaseAttribute);

            if (lastRelease == null || compareVersions(release, lastRelease) > 0) {
                lastRelease = release;
            }
        }

        if (lastRelease == null) {
            throw new IllegalArgumentException("Impossibile individuare l'ultima release.");
        }

        return lastRelease;
    }

    private static List<Instance> filterByRelease(Instances data, String release) {
        Attribute releaseAttribute = data.attribute(RELEASE);
        List<Instance> result = new ArrayList<>();

        for (int i = 0; i < data.numInstances(); i++) {
            Instance instance = data.instance(i);

            if (!instance.isMissing(releaseAttribute)) {
                String currentRelease = getStringValue(instance, releaseAttribute);

                if (currentRelease.equals(release)) {
                    result.add(instance);
                }
            }
        }

        return result;
    }

    /**
     * Criterio conservativo:
     * una classe viene esclusa solo se è contemporaneamente:
     *
     * - nel 25% più basso per LOC;
     * - con complessità ciclomatica <= 1;
     * - con numero di branch <= 1;
     * - con profondità massima di annidamento <= 1.
     */
    private static boolean isTooSmallAndSimple(Instance instance, double locQ1) {
        double loc = value(instance, LOC);
        double branches = value(instance, N_BRANCHES);
        double nesting = value(instance, MAX_NESTING_DEPTH);
        double smells = value(instance, NSMELLS);

        boolean tooSmallAbsolute = loc < 150.0;

        boolean noCodeSmells = smells <= 0.0;

        boolean tooSmallAndSimple = loc <= locQ1
                && branches <= 1.0
                && nesting <= 1.0;

        return tooSmallAbsolute || noCodeSmells || tooSmallAndSimple;
    }

    private static Comparator<Instance> buildRankingComparator(Instances data) {
        Attribute nSmellsAttribute = data.attribute(NSMELLS);
        Attribute locAttribute = data.attribute(LOC);
        Attribute cyclomaticAttribute = data.attribute(CYCLOMATIC_COMPLEXITY);

        return Comparator
                .comparingDouble((Instance i) -> safeValue(i, nSmellsAttribute)).reversed()
                .thenComparing(Comparator.comparingDouble((Instance i) -> safeValue(i, locAttribute)).reversed())
                .thenComparing(Comparator.comparingDouble((Instance i) -> safeValue(i, cyclomaticAttribute)).reversed());
    }

    private static double computeFirstQuartile(List<Instance> instances, Attribute attribute) {
        List<Double> values = new ArrayList<>();

        for (Instance instance : instances) {
            if (!instance.isMissing(attribute)) {
                values.add(instance.value(attribute));
            }
        }

        if (values.isEmpty()) {
            throw new IllegalArgumentException("Impossibile calcolare Q1: nessun valore valido per " + attribute.name());
        }

        Collections.sort(values);

        double position = 0.25 * (values.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);

        if (lowerIndex == upperIndex) {
            return values.get(lowerIndex);
        }

        double weight = position - lowerIndex;

        return values.get(lowerIndex) * (1.0 - weight) + values.get(upperIndex) * weight;
    }

    private static void saveSelectedClasses(
            Instances originalData,
            List<Instance> selectedClasses,
            String outputPath
    ) throws IOException {
        File outputFile = new File(outputPath);

        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write("rank");

            for (int i = 0; i < originalData.numAttributes(); i++) {
                writer.write(",");
                writer.write(escapeCsv(originalData.attribute(i).name()));
            }

            writer.newLine();

            int rank = 1;

            for (Instance instance : selectedClasses) {
                writer.write(String.valueOf(rank));

                for (int i = 0; i < originalData.numAttributes(); i++) {
                    writer.write(",");
                    writer.write(escapeCsv(getValueForCsv(instance, originalData.attribute(i))));
                }

                writer.newLine();
                rank++;
            }
        }
    }

    private static String getValueForCsv(Instance instance, Attribute attribute) {
        if (instance.isMissing(attribute)) {
            return "";
        }

        if (attribute.isNominal() || attribute.isString()) {
            return instance.stringValue(attribute);
        }

        double value = instance.value(attribute);

        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }

        return String.valueOf(value);
    }

    private static String getStringValue(Instance instance, Attribute attribute) {
        if (attribute.isNominal() || attribute.isString()) {
            return instance.stringValue(attribute).trim();
        }

        return instance.toString(attribute).trim();
    }

    private static double value(Instance instance, String attributeName) {
        Attribute attribute = instance.dataset().attribute(attributeName);

        if (attribute == null || instance.isMissing(attribute)) {
            return Double.NaN;
        }

        return instance.value(attribute);
    }

    private static double safeValue(Instance instance, Attribute attribute) {
        if (attribute == null || instance.isMissing(attribute)) {
            return Double.NEGATIVE_INFINITY;
        }

        return instance.value(attribute);
    }

    private static int compareVersions(String v1, String v2) {
        List<Integer> p1 = extractVersionNumbers(v1);
        List<Integer> p2 = extractVersionNumbers(v2);

        int max = Math.max(p1.size(), p2.size());

        for (int i = 0; i < max; i++) {
            int n1 = i < p1.size() ? p1.get(i) : 0;
            int n2 = i < p2.size() ? p2.get(i) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        return v1.compareTo(v2);
    }

    private static List<Integer> extractVersionNumbers(String version) {
        List<Integer> numbers = new ArrayList<>();

        String[] parts = version.split("[^0-9]+");

        for (String part : parts) {
            if (!part.isBlank()) {
                numbers.add(Integer.parseInt(part));
            }
        }

        return numbers;
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
}
