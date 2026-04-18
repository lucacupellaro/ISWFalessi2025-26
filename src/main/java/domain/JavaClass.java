package domain;

import java.util.ArrayList;
import java.util.List;

public class JavaClass {

    private final String name;
    private final String path;
    private final String release;
    private String content;
    private boolean buggy;
    private int nSmells;

    private int loc;
    private int commentLines;
    private int nRevisions;
    private int nAuth;
    private int nFix;

    private int locAdded;
    private int maxLocAdded;
    private double avgLocAdded;
    // Change Set
    private int changeSetSize;       // somma
    private int maxChangeSet;
    private double avgChangeSet;

    // Age
    private double age;              // settimane dalla prima modifica alla release
    private double weightedAge;      // age ponderata per LOC touched

    private int churn;
    private int maxChurn;
    private double avgChurn;

    private int locTouched;

    private int cyclomaticComplexity;
    private int duplication;
    private int nBranches;
    private int maxNestingDepth;

    private final List<String> touchedPaths = new ArrayList<>();
    private final List<Integer> locAddedList = new ArrayList<>();
    private final List<Integer> locRemovedList = new ArrayList<>();

    public JavaClass(String name, String path, String release) {
        this.name = name;
        this.path = path;
        this.release = release;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getRelease() { return release; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isBuggy() { return buggy; }
    public void setBuggy(boolean buggy) { this.buggy = buggy; }

    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }



    public int getNSmells() { return nSmells; }
    public void setNSmells(int nSmells) { this.nSmells = nSmells; }

    public int getCommentLines() { return commentLines; }
    public void setCommentLines(int commentLines) { this.commentLines = commentLines; }

    public int getNRevisions() { return nRevisions; }
    public void setNRevisions(int nRevisions) { this.nRevisions = nRevisions; }

    public int getNAuth() { return nAuth; }
    public void setNAuth(int nAuth) { this.nAuth = nAuth; }

    public int getNFix() { return nFix; }
    public void setNFix(int nFix) { this.nFix = nFix; }

    public int getLocAdded() { return locAdded; }
    public void sumLocAdded(int val) { this.locAdded += val; }

    public int getMaxLocAdded() { return maxLocAdded; }
    public void setMaxLocAdded(int maxLocAdded) { this.maxLocAdded = maxLocAdded; }

    public double getAvgLocAdded() { return avgLocAdded; }
    public void setAvgLocAdded(double avgLocAdded) { this.avgLocAdded = avgLocAdded; }

    public int getChurn() { return churn; }
    public void sumChurn(int val) { this.churn += val; }

    public int getMaxChurn() { return maxChurn; }
    public void setMaxChurn(int maxChurn) { this.maxChurn = maxChurn; }

    public double getAvgChurn() { return avgChurn; }
    public void setAvgChurn(double avgChurn) { this.avgChurn = avgChurn; }

    public int getLocTouched() { return locTouched; }
    public void addLocTouched(int val) { this.locTouched += val; }

    public List<Integer> getLocAddedList() { return locAddedList; }
    public void addLocAdded(int val) { this.locAddedList.add(val); }

    public List<Integer> getLocRemovedList() { return locRemovedList; }
    public void addLocRemoved(int val) { this.locRemovedList.add(val); }

    public int getChangeSetSize() { return changeSetSize; }
    public void setChangeSetSize(int val) { this.changeSetSize = val; }

    public int getMaxChangeSet() { return maxChangeSet; }
    public void setMaxChangeSet(int maxChangeSet) { this.maxChangeSet = maxChangeSet; }

    public double getAvgChangeSet() { return avgChangeSet; }
    public void setAvgChangeSet(double avgChangeSet) { this.avgChangeSet = avgChangeSet; }

    public double getAge() { return age; }
    public void setAge(double age) { this.age = age; }

    public double getWeightedAge() { return weightedAge; }
    public void setWeightedAge(double weightedAge) { this.weightedAge = weightedAge; }

    public int getCyclomaticComplexity() { return cyclomaticComplexity; }
    public void setCyclomaticComplexity(int cyclomaticComplexity) { this.cyclomaticComplexity = cyclomaticComplexity; }

    public int getDuplication() { return duplication; }
    public void setDuplication(int duplication) { this.duplication = duplication; }

    public int getNBranches() { return nBranches; }
    public void setNBranches(int nBranches) { this.nBranches = nBranches; }

    public int getMaxNestingDepth() { return maxNestingDepth; }
    public void setMaxNestingDepth(int maxNestingDepth) { this.maxNestingDepth = maxNestingDepth; }

    public List<String> getTouchedPaths() { return touchedPaths; }
    public void addTouchedPath(String path) { this.touchedPaths.add(path); }
}