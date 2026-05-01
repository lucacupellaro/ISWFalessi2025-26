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

    private ClassRecord(Builder builder) {
        this.release = builder.release;
        this.className = builder.className;
        this.loc = builder.loc;
        this.commentLines = builder.commentLines;
        this.nRevisions = builder.nRevisions;
        this.nAuth = builder.nAuth;
        this.nFix = builder.nFix;
        this.locAdded = builder.locAdded;
        this.maxLocAdded = builder.maxLocAdded;
        this.avgLocAdded = builder.avgLocAdded;
        this.churn = builder.churn;
        this.maxChurn = builder.maxChurn;
        this.avgChurn = builder.avgChurn;
        this.locTouched = builder.locTouched;
        this.changeSetSize = builder.changeSetSize;
        this.maxChangeSet = builder.maxChangeSet;
        this.avgChangeSet = builder.avgChangeSet;
        this.age = builder.age;
        this.weightedAge = builder.weightedAge;
        this.cyclomaticComplexity = builder.cyclomaticComplexity;
        this.duplication = builder.duplication;
        this.nBranches = builder.nBranches;
        this.maxNestingDepth = builder.maxNestingDepth;
        this.buggy = builder.buggy;
        this.nSmells = builder.nSmells;
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

    public static class Builder {
        private String release;
        private String className;
        private int loc;
        private int commentLines;
        private int nRevisions;
        private int nAuth;
        private int nFix;
        private int locAdded;
        private int maxLocAdded;
        private double avgLocAdded;
        private int churn;
        private int maxChurn;
        private double avgChurn;
        private int locTouched;
        private int changeSetSize;
        private int maxChangeSet;
        private double avgChangeSet;
        private double age;
        private double weightedAge;
        private int cyclomaticComplexity;
        private int duplication;
        private int nBranches;
        private int maxNestingDepth;
        private boolean buggy;
        private int nSmells;

        public Builder setRelease(String release) { this.release = release; return this; }
        public Builder setClassName(String className) { this.className = className; return this; }
        public Builder setLoc(int loc) { this.loc = loc; return this; }
        public Builder setCommentLines(int commentLines) { this.commentLines = commentLines; return this; }
        public Builder setNRevisions(int nRevisions) { this.nRevisions = nRevisions; return this; }
        public Builder setNAuth(int nAuth) { this.nAuth = nAuth; return this; }
        public Builder setNFix(int nFix) { this.nFix = nFix; return this; }
        public Builder setLocAdded(int locAdded) { this.locAdded = locAdded; return this; }
        public Builder setMaxLocAdded(int maxLocAdded) { this.maxLocAdded = maxLocAdded; return this; }
        public Builder setAvgLocAdded(double avgLocAdded) { this.avgLocAdded = avgLocAdded; return this; }
        public Builder setChurn(int churn) { this.churn = churn; return this; }
        public Builder setMaxChurn(int maxChurn) { this.maxChurn = maxChurn; return this; }
        public Builder setAvgChurn(double avgChurn) { this.avgChurn = avgChurn; return this; }
        public Builder setLocTouched(int locTouched) { this.locTouched = locTouched; return this; }
        public Builder setChangeSetSize(int changeSetSize) { this.changeSetSize = changeSetSize; return this; }
        public Builder setMaxChangeSet(int maxChangeSet) { this.maxChangeSet = maxChangeSet; return this; }
        public Builder setAvgChangeSet(double avgChangeSet) { this.avgChangeSet = avgChangeSet; return this; }
        public Builder setAge(double age) { this.age = age; return this; }
        public Builder setWeightedAge(double weightedAge) { this.weightedAge = weightedAge; return this; }
        public Builder setCyclomaticComplexity(int cyclomaticComplexity) { this.cyclomaticComplexity = cyclomaticComplexity; return this; }
        public Builder setDuplication(int duplication) { this.duplication = duplication; return this; }
        public Builder setNBranches(int nBranches) { this.nBranches = nBranches; return this; }
        public Builder setMaxNestingDepth(int maxNestingDepth) { this.maxNestingDepth = maxNestingDepth; return this; }
        public Builder setBuggy(boolean buggy) { this.buggy = buggy; return this; }
        public Builder setNSmells(int nSmells) { this.nSmells = nSmells; return this; }

        public ClassRecord build() {
            return new ClassRecord(this);
        }
    }
}