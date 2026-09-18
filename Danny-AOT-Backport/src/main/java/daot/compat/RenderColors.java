package daot.compat;
public final class RenderColors {
    private RenderColors() {}
    public static float red(int c) { return (c >>> 16 & 255) / 255F; }
    public static float green(int c) { return (c >>> 8 & 255) / 255F; }
    public static float blue(int c) { return (c & 255) / 255F; }
    public static float alpha(int c) { return (c >>> 24) / 255F; }
    public static int pack(float r,float g,float b,float a) {
        return byteValue(a)<<24 | byteValue(r)<<16 | byteValue(g)<<8 | byteValue(b);
    }
    private static int byteValue(float v) { return Math.max(0,Math.min(255,Math.round(v*255))); }
}
