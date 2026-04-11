package domain;



public class JavaClass {

    private final String name;
    private final String path;
    private final String release;
    private int loc;
    private boolean buggy;

    public JavaClass(String name, String path, String release) {
        this.name = name;
        this.path = path;
        this.release = release;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public String getRelease() { return release; }
    public int getLoc() { return loc; }
    public void setLoc(int loc) { this.loc = loc; }
    public boolean isBuggy() { return buggy; }
    public void setBuggy(boolean buggy) { this.buggy = buggy; }
}