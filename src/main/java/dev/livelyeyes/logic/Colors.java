package dev.livelyeyes.logic;

/** ARGB helpers (0xAARRGGBB). Distances use the "redmean" approximation, cheap and close enough to perception. */
public final class Colors {
    private Colors() {
    }

    public static int a(int c) {
        return c >>> 24;
    }

    public static int r(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int g(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int b(int c) {
        return c & 0xFF;
    }

    public static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** 0 for equal colors, ~765 for black against white. */
    public static double distance(int c1, int c2) {
        double rMean = (r(c1) + r(c2)) / 2.0;
        int dr = r(c1) - r(c2), dg = g(c1) - g(c2), db = b(c1) - b(c2);
        return Math.sqrt((2 + rMean / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rMean) / 256) * db * db);
    }

    public static double luma(int c) {
        return 0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c);
    }

    /** HSV saturation, 0..1. */
    public static double saturation(int c) {
        int max = Math.max(r(c), Math.max(g(c), b(c)));
        int min = Math.min(r(c), Math.min(g(c), b(c)));
        return max == 0 ? 0 : (max - min) / (double) max;
    }

    /** Linear mix, t = 0 gives c1, t = 1 gives c2. Result is opaque. */
    public static int mix(int c1, int c2, double t) {
        return argb(255,
                (int) Math.round(r(c1) + (r(c2) - r(c1)) * t),
                (int) Math.round(g(c1) + (g(c2) - g(c1)) * t),
                (int) Math.round(b(c1) + (b(c2) - b(c1)) * t));
    }

    public static int opaque(int c) {
        return c | 0xFF000000;
    }
}
