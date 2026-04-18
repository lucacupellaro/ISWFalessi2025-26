package domain;

public class ClassRecord {

    private final String release;
    private final String className;
    private final int loc;
    private final int commentLines;
    private final int nRevisions;
    private final int nAuth;
    private final int nFix;
    private final int locAdded;
    private final int maxLocAdded;
    private final double avgLocAdded;
    private final int churn;
    private final int maxChurn;
    private final double avgChurn;
    private final int locTouched;
    private final int changeSetSize;
    private final int maxChangeSet;
    private final double avgChangeSet;
    private final double age;
    private final double weightedAge;
    private final int cyclomaticComplexity;
    private final int duplication;
    private final int nBranches;
    private final int maxNestingDepth;
    private boolean buggy;
    private final int nSmells;


    public ClassRecord(String release, String className, int loc, int commentLines,
                       int nRevisions, int nAuth, int nFix, int locAdded,
                       int maxLocAdded, double avgLocAdded, int churn,
                       int maxChurn, double avgChurn, int locTouched,
                       int changeSetSize, int maxChangeSet, double avgChangeSet,
                       double age, double weightedAge,
                       int cyclomaticComplexity, int duplication,
                       int nBranches, int maxNestingDepth,
                       boolean buggy, int nSmells) {
        this.release = release;
        this.className = className;
        this.loc = loc;
        this.commentLines = commentLines;
        this.nRevisions = nRevisions;
        this.nAuth = nAuth;
        this.nFix = nFix;
        this.locAdded = locAdded;
        this.maxLocAdded = maxLocAdded;
        this.avgLocAdded = avgLocAdded;
        this.churn = churn;
        this.maxChurn = maxChurn;
        this.avgChurn = avgChurn;
        this.locTouched = locTouched;
        this.changeSetSize = changeSetSize;
        this.maxChangeSet = maxChangeSet;
        this.avgChangeSet = avgChangeSet;
        this.age = age;
        this.weightedAge = weightedAge;
        this.cyclomaticComplexity = cyclomaticComplexity;
        this.duplication = duplication;
        this.nBranches = nBranches;
        this.maxNestingDepth = maxNestingDepth;
        this.buggy = buggy;
        this.nSmells = nSmells;

    }

    public String getRelease() { return release; }
    public String getClassName() { return className; }
    public int getLoc() { return loc; }
    public int getCommentLines() { return commentLines; }
    public int getNRevisions() { return nRevisions; }
    public int getNAuth() { return nAuth; }
    public int getNFix() { return nFix; }
    public int getLocAdded() { return locAdded; }
    public int getMaxLocAdded() { return maxLocAdded; }
    public double getAvgLocAdded() { return avgLocAdded; }
    public int getChurn() { return churn; }
    public int getMaxChurn() { return maxChurn; }
    public double getAvgChurn() { return avgChurn; }
    public int getLocTouched() { return locTouched; }
    public int getChangeSetSize() { return changeSetSize; }
    public int getMaxChangeSet() { return maxChangeSet; }
    public double getAvgChangeSet() { return avgChangeSet; }
    public double getAge() { return age; }
    public double getWeightedAge() { return weightedAge; }
    public int getCyclomaticComplexity() { return cyclomaticComplexity; }
    public int getDuplication() { return duplication; }
    public int getNBranches() { return nBranches; }
    public int getMaxNestingDepth() { return maxNestingDepth; }
    public boolean isBuggy() { return buggy; }
    public int getNSmells() { return nSmells; }
}