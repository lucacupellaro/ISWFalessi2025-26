package domain;


public class ClassRecord {

    private final String release;
    private final String className;
    private final int loc;
    private boolean buggy;

    public ClassRecord(String release, String className, int loc, boolean buggy) {
        this.release = release;
        this.className = className;
        this.loc = loc;
        this.buggy = buggy;
    }

    public String getRelease() { return release; }
    public String getClassName() { return className; }
    public int getLoc() { return loc; }
    public boolean isBuggy() { return buggy; }
}