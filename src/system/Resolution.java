package system;

import annotation.HotCode;

@HotCode
public class Resolution {
    private int width;
    private int height;

    public Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public void setWidth(int val) { width = val; }
    public int getHeight() { return height; }
    public void setHeight(int val) { height = val; }
}
