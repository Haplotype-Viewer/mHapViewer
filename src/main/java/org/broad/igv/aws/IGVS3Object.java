package org.broad.igv.aws;

public class IGVS3Object {

    private String name;

    private boolean isDir;

    public IGVS3Object(String name) {
        this.name = name;
        this.isDir = false;
    }

    public IGVS3Object(String name, boolean isDir) {
        this.name = name;
        this.isDir = isDir;
    }

    public String toString() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDir() {
        return isDir;
    }

    public void setDir(boolean dir) {
        isDir = dir;
    }
}
