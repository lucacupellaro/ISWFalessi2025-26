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
    private boolean buggy;

    public ClassRecord(String release, String className, int loc, int commentLines,
                       int nRevisions, int nAuth, int nFix, int locAdded,
                       int maxLocAdded, double avgLocAdded, int churn,
                       int maxChurn, double avgChurn, int locTouched, boolean buggy) {
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
        this.buggy = buggy;
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
    public boolean isBuggy() { return buggy; }
}