package system;

import annotation.HotCode;

@HotCode
public class Resolution {
    private static int width;
    private static int height;

    public static int getWidth() { return width; }
    public static void setWidth(int val) { width = val; }
    public static int getHeight() { return height; }
    public static void setHeight(int val) { height = val; }
}
