package dev.livelyeyes.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the eyes on the front of a 64x64 skin's head. No fixed template: it learns the skin tone and the hair
 * colors from the face itself, then searches every horizontal band for two blobs that stand out from the skin,
 * mirror each other across the middle of the face and look like eyes (white of the eye, contrast, usual height).
 */
public final class FaceAnalyzer {
    public static final int SKIN_SIZE = 64;
    public static final int FACE = 8;
    public static final int BASE_X = 8, BASE_Y = 8;
    public static final int HAT_X = 40, HAT_Y = 8;
    /** A hat-layer pixel at or above this alpha hides the base face under it. */
    public static final int HAT_OPAQUE = 200;
    /** Bands that score below this are not trusted as eyes. */
    public static final double MIN_SCORE = 2.4;

    /** Colors closer than this belong to the same family (skin tone, one hair tone). */
    static final double SAME_COLOR = 45;
    /** A pixel this close to any hair tone counts as hair-colored (darker or lighter strands included). */
    static final double HAIR_NEAR = 75;
    /** Minimum distance from the skin tone for a pixel to be part of an eye. Skin shading and blush stay below. */
    static final double EYE_DIFF = 90;
    /** Inside one eye, pixels much weaker than its strongest pixel are shading around it, not the eye. */
    static final double WEAK_FRACTION = 0.35;
    /** Pixels beside an eye join it from this distance (dark irises on dark skin are ~95; skin shading stays below). */
    static final double GROW_DIFF = 80;

    private FaceAnalyzer() {
    }

    /** Why no eyes were accepted. */
    public enum Failure { NOT_64X64, NO_SKIN_TONE, NO_PAIR, TOO_WEAK }

    /** Either a layout, or the failure and (for TOO_WEAK) the best score seen. */
    public record Result(EyeLayout layout, Failure failure, double bestScore) {
        public boolean found() {
            return layout != null;
        }

        public String describe() {
            if (found()) {
                return String.format("score %.2f, left %s, right %s", layout.score(), layout.left().describe(),
                        layout.right().describe());
            }
            return switch (failure) {
                case NOT_64X64 -> "skin is not 64x64";
                case NO_SKIN_TONE -> "no skin tone on the face (mask or helmet?)";
                case NO_PAIR -> "no pair of eyes that mirror each other";
                case TOO_WEAK -> String.format("best guess too weak (%.2f < %.2f)", bestScore, MIN_SCORE);
            };
        }
    }

    public static int faceIndex(int x, int y) {
        return y * FACE + x;
    }

    public static int baseIndex(int x, int y) {
        return (BASE_Y + y) * SKIN_SIZE + BASE_X + x;
    }

    public static int hatIndex(int x, int y) {
        return (HAT_Y + y) * SKIN_SIZE + HAT_X + x;
    }

    /** What the front of the head shows: the hat layer drawn over the base face. */
    public static int[] composite(int[] skin) {
        int[] face = new int[FACE * FACE];
        for (int y = 0; y < FACE; y++) {
            for (int x = 0; x < FACE; x++) {
                int base = Colors.opaque(skin[baseIndex(x, y)]);
                int hat = skin[hatIndex(x, y)];
                int alpha = Colors.a(hat);
                face[faceIndex(x, y)] = alpha == 0 ? base : Colors.mix(base, hat, alpha / 255.0);
            }
        }
        return face;
    }

    public static Result analyze(int[] skin) {
        if (skin == null || skin.length != SKIN_SIZE * SKIN_SIZE) {
            return new Result(null, Failure.NOT_64X64, 0);
        }
        int[] face = composite(skin);

        int skinColor = findSkinTone(face);
        if (skinColor == 0) {
            return new Result(null, Failure.NO_SKIN_TONE, 0);
        }

        int[] hairTones = findHairTones(face, skinColor);
        boolean[] hair = hairMask(face, hairTones);
        boolean[] eye = eyeMask(face, skinColor, hair);

        Candidate best = null;
        for (int top = 1; top <= 6; top++) {
            for (int h = 1; h <= 3; h++) {
                int bottom = top + h - 1;
                if (bottom > 6) {
                    continue;
                }
                Candidate c = score(face, eye, top, bottom, skinColor, hairTones);
                if (c != null && (best == null || c.score > best.score)) {
                    best = c;
                }
            }
        }
        if (best == null) {
            return new Result(null, Failure.NO_PAIR, 0);
        }
        if (best.score < MIN_SCORE) {
            return new Result(null, Failure.TOO_WEAK, best.score);
        }

        List<int[]> leftPixels = new ArrayList<>(best.left), rightPixels = new ArrayList<>(best.right);
        growSideways(face, leftPixels, rightPixels, skinColor, hair);
        EyeLayout.Eye left = toEye(face, leftPixels, skinColor);
        EyeLayout.Eye right = toEye(face, rightPixels, skinColor);
        // the closed eye is a line of darker skin: a lash line in the iris color came out green or purple
        int lash = Colors.mix(skinColor, 0xFF000000, 0.5);
        return new Result(new EyeLayout(left, right, skinColor, skinColor, lash, best.score), null, best.score);
    }

    /**
     * On dark skin the iris can be close to the skin tone and miss the eye threshold while the white of the eye
     * passes. Pulls in pixels right beside an eye, on its rows, that still differ from the skin, but only in mirrored
     * pairs, never into the two middle columns (the nose) nor the outer edge (face shading and side hair).
     */
    static void growSideways(int[] face, List<int[]> left, List<int[]> right, int skinColor, boolean[] hair) {
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int[] p : List.copyOf(left)) {
                for (int dx : new int[]{-1, 1}) {
                    int x = p[0] + dx, y = p[1], mx = FACE - 1 - x;
                    if (x < 1 || x > 2 || contains(left, x, y) || contains(right, mx, y)) {
                        continue;
                    }
                    if (sideCandidate(face, x, y, skinColor, hair) && sideCandidate(face, mx, y, skinColor, hair)
                            && contains(right, FACE - 1 - p[0], y)) {
                        left.add(new int[]{x, y});
                        right.add(new int[]{mx, y});
                        grew = true;
                    }
                }
            }
        }
    }

    private static boolean sideCandidate(int[] face, int x, int y, int skinColor, boolean[] hair) {
        int i = faceIndex(x, y);
        return !hair[i] && Colors.distance(face[i], skinColor) > GROW_DIFF;
    }

    private static boolean contains(List<int[]> pixels, int x, int y) {
        return pixels.stream().anyMatch(p -> p[0] == x && p[1] == y);
    }

    /** Pixels that stand out from the skin and are not hair: where eyes (and noses, mouths...) can be. */
    static boolean[] eyeMask(int[] face, int skinColor, boolean[] hair) {
        boolean[] eye = new boolean[FACE * FACE];
        for (int i = 0; i < eye.length; i++) {
            eye[i] = !hair[i] && Colors.distance(face[i], skinColor) > EYE_DIFF;
        }
        return eye;
    }

    // ---- skin and hair ----------------------------------------------------------------------------------------

    /** Most common color family in the lower part of the face, away from the side columns. 0 if too rare. */
    static int findSkinTone(int[] face) {
        List<Integer> samples = new ArrayList<>();
        for (int y = 3; y < FACE; y++) {
            for (int x = 1; x < FACE - 1; x++) {
                samples.add(face[faceIndex(x, y)]);
            }
        }
        Cluster c = clusters(samples, SAME_COLOR).get(0);
        return c.count >= 6 ? c.mode : 0;
    }

    /** Tones of the top two rows that are clearly not skin, each seen at least twice. Empty for bald heads. */
    static int[] findHairTones(int[] face, int skinColor) {
        List<Integer> samples = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < FACE; x++) {
                samples.add(face[faceIndex(x, y)]);
            }
        }
        return clusters(samples, SAME_COLOR).stream()
                .filter(c -> c.count >= 2 && Colors.distance(c.mode, skinColor) > EYE_DIFF)
                .mapToInt(Cluster::mode)
                .toArray();
    }

    static boolean hairLike(int color, int[] hairTones) {
        for (int t : hairTones) {
            if (Colors.distance(color, t) < HAIR_NEAR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Hair is everything hair-colored that connects to the top row. Below the forehead (row 3 on) it may only run
     * down the sides (columns 0 and 7, and 1 and 6 next to hair already there), which keeps it out of the eyes.
     */
    static boolean[] hairMask(int[] face, int[] hairTones) {
        boolean[] hair = new boolean[FACE * FACE];
        if (hairTones.length == 0) {
            return hair;
        }
        // row by row from the top, repeated until stable, so side locks grow downwards and sideways
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < FACE; y++) {
                for (int x = 0; x < FACE; x++) {
                    int i = faceIndex(x, y);
                    if (hair[i] || !hairLike(face[i], hairTones) || !hairMayGrowInto(hair, x, y)) {
                        continue;
                    }
                    hair[i] = true;
                    changed = true;
                }
            }
        }
        return hair;
    }

    private static boolean hairMayGrowInto(boolean[] hair, int x, int y) {
        boolean touches = y == 0
                || hair[faceIndex(x, y - 1)]
                || x > 0 && hair[faceIndex(x - 1, y)]
                || x < FACE - 1 && hair[faceIndex(x + 1, y)];
        if (!touches) {
            return false;
        }
        if (y <= 2 || x == 0 || x == FACE - 1) {
            return true;
        }
        if (x == 1) {
            return hair[faceIndex(0, y)];
        }
        if (x == FACE - 2) {
            return hair[faceIndex(FACE - 1, y)];
        }
        return false;
    }

    record Cluster(int mode, int count) {
    }

    /**
     * Groups colors around their most frequent members, biggest group first. Each group is represented by its most
     * common exact color, which matches the skin's own palette better than an average would.
     */
    static List<Cluster> clusters(List<Integer> colors, double radius) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int c : colors) {
            counts.merge(Colors.opaque(c), 1, Integer::sum);
        }
        List<Map.Entry<Integer, Integer>> byCount = new ArrayList<>(counts.entrySet());
        byCount.sort((a, b) -> !a.getValue().equals(b.getValue())
                ? Integer.compare(b.getValue(), a.getValue())
                : Integer.compare(a.getKey(), b.getKey()));
        List<int[]> groups = new ArrayList<>(); // {mode, count}
        for (Map.Entry<Integer, Integer> e : byCount) {
            int[] home = null;
            for (int[] g : groups) {
                if (Colors.distance(g[0], e.getKey()) < radius) {
                    home = g;
                    break;
                }
            }
            if (home == null) {
                groups.add(new int[]{e.getKey(), e.getValue()});
            } else {
                home[1] += e.getValue();
            }
        }
        groups.sort((a, b) -> Integer.compare(b[1], a[1]));
        return groups.stream().map(g -> new Cluster(g[0], g[1])).toList();
    }

    // ---- band search ------------------------------------------------------------------------------------------

    record Candidate(List<int[]> left, List<int[]> right, double score) {
    }

    static Candidate score(int[] face, boolean[] eye, int top, int bottom, int skinColor, int[] hairTones) {
        List<int[]> left = new ArrayList<>(), right = new ArrayList<>();
        for (int y = top; y <= bottom; y++) {
            for (int x = 0; x < FACE; x++) {
                if (eye[faceIndex(x, y)]) {
                    (x < FACE / 2 ? left : right).add(new int[]{x, y});
                }
            }
        }
        left = dropWeak(face, left, skinColor);
        right = dropWeak(face, right, skinColor);
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        List<int[]> all = concat(left, right);
        // the band must be tight: something on its first and on its last row
        if (all.stream().noneMatch(p -> p[1] == top) || all.stream().noneMatch(p -> p[1] == bottom)) {
            return null;
        }
        if (left.size() > 8 || right.size() > 8 || !connected(left) || !connected(right)) {
            return null;
        }
        int leftMax = left.stream().mapToInt(p -> p[0]).max().orElseThrow();
        int rightMin = right.stream().mapToInt(p -> p[0]).min().orElseThrow();
        if (leftMax >= rightMin - 1) {
            return null; // one blob across the middle: nose, mouth or a mask, not two eyes
        }

        Set<Integer> mirrored = new HashSet<>(), rightSet = new HashSet<>();
        for (int[] p : left) {
            mirrored.add(faceIndex(FACE - 1 - p[0], p[1]));
        }
        for (int[] p : right) {
            rightSet.add(faceIndex(p[0], p[1]));
        }
        Set<Integer> union = new HashSet<>(mirrored);
        union.addAll(rightSet);
        mirrored.retainAll(rightSet);
        double symmetry = mirrored.size() / (double) union.size();

        double contrast = 0;
        for (int[] p : all) {
            contrast += Math.min(1, Colors.distance(face[faceIndex(p[0], p[1])], skinColor) / 250);
        }
        contrast /= all.size();

        double sclera = (hasSclera(face, left, skinColor) ? 0.5 : 0) + (hasSclera(face, right, skinColor) ? 0.5 : 0);
        double rowPrior = -0.35 * Math.abs((top + bottom) / 2.0 - 4);

        // a row made only of hair-colored pixels is an eyebrow or a bang, not an eye
        double hairPenalty = 0;
        for (int y = top; y <= bottom; y++) {
            final int row = y;
            boolean allHair = all.stream().filter(p -> p[1] == row)
                    .allMatch(p -> hairLike(face[faceIndex(p[0], p[1])], hairTones));
            if (allHair && hairTones.length > 0) {
                hairPenalty -= bottom > top ? 0.8 : 0.5;
            }
        }

        // the band should hold whole blobs: eye pixels right above or below it mean it cut an eye in half
        Set<Integer> outside = new HashSet<>();
        for (int[] p : all) {
            for (int dy : new int[]{-1, 1}) {
                int ny = p[1] + dy;
                if (ny >= top && ny <= bottom || ny < 0 || ny >= FACE) {
                    continue;
                }
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = p[0] + dx;
                    if (nx < 0 || nx >= FACE || (nx < FACE / 2) != (p[0] < FACE / 2)) {
                        continue;
                    }
                    int i = faceIndex(nx, ny);
                    if (eye[i] && !hairLike(face[i], hairTones)) {
                        outside.add(i);
                    }
                }
            }
        }
        double cut = -Math.min(1.5, 0.35 * outside.size());

        double size = -0.15 * Math.max(0, all.size() - 8);
        double tall = bottom - top >= 2 ? -0.3 : 0;
        double total = 2 * symmetry + 1.5 * contrast + sclera + rowPrior + hairPenalty + cut + size + tall;
        return new Candidate(left, right, total);
    }

    /** Drops pixels far weaker than the strongest one on that side (skin shading next to the eye). */
    private static List<int[]> dropWeak(int[] face, List<int[]> side, int skinColor) {
        double strongest = 0;
        for (int[] p : side) {
            strongest = Math.max(strongest, Colors.distance(face[faceIndex(p[0], p[1])], skinColor));
        }
        List<int[]> kept = new ArrayList<>();
        for (int[] p : side) {
            int c = face[faceIndex(p[0], p[1])];
            if (isSclera(c, skinColor) || Colors.distance(c, skinColor) >= WEAK_FRACTION * strongest) {
                kept.add(p);
            }
        }
        return kept;
    }

    static boolean isSclera(int color, int skinColor) {
        double l = Colors.luma(color);
        return l >= 160 && Colors.saturation(color) <= 0.3 && l >= Colors.luma(skinColor) + 25;
    }

    private static boolean hasSclera(int[] face, List<int[]> eye, int skinColor) {
        return eye.stream().anyMatch(p -> isSclera(face[faceIndex(p[0], p[1])], skinColor));
    }

    /** 8-neighborhood connectivity. */
    static boolean connected(List<int[]> pixels) {
        Set<Integer> all = new HashSet<>();
        for (int[] p : pixels) {
            all.add(faceIndex(p[0], p[1]));
        }
        Set<Integer> seen = new HashSet<>();
        ArrayList<int[]> stack = new ArrayList<>();
        stack.add(pixels.get(0));
        while (!stack.isEmpty()) {
            int[] p = stack.remove(stack.size() - 1);
            if (!seen.add(faceIndex(p[0], p[1]))) {
                continue;
            }
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = p[0] + dx, ny = p[1] + dy;
                    if (nx >= 0 && nx < FACE && ny >= 0 && ny < FACE && all.contains(faceIndex(nx, ny))
                            && !seen.contains(faceIndex(nx, ny))) {
                        stack.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return seen.size() == all.size();
    }

    private static List<int[]> concat(List<int[]> a, List<int[]> b) {
        List<int[]> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static EyeLayout.Eye toEye(int[] face, List<int[]> pixels, int skinColor) {
        List<EyeLayout.Pixel> out = new ArrayList<>();
        for (int[] p : pixels) {
            out.add(new EyeLayout.Pixel(p[0], p[1], isSclera(face[faceIndex(p[0], p[1])], skinColor)));
        }
        return new EyeLayout.Eye(out);
    }
}
