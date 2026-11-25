package dev.livelyeyes.logic;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints an expression onto a copy of the skin: eyelids, irises, brows, cheeks and tears. Only the front of the head
 * changes, and only around the eyes the analyzer found.
 */
public final class FacePainter {
    static final int BLUSH = 0xFFFF5C7A;
    static final int COLD = 0xFF7EC4FF;
    static final int TEAR = 0xFF8CD8FF;
    static final int WHITE = 0xFFF4F4F4;
    /** Pixels further than this from the skin tone (hair, beard, mask, freckles) are never painted over. */
    static final double SKIN_NEAR = 70;

    private FacePainter() {
    }

    public static int[] paint(int[] skin, EyeLayout layout, FaceState state) {
        int[] out = skin.clone();
        int[] face = FaceAnalyzer.composite(skin);
        FaceState s = layout.normalize(state);
        paintEye(out, skin, face, layout, layout.left(), s.left());
        paintEye(out, skin, face, layout, layout.right(), s.right());
        if (s.brows() == FaceState.Brows.ANGRY) {
            angryBrow(out, face, layout, layout.left(), true);
            angryBrow(out, face, layout, layout.right(), false);
        }
        if (s.cheeks() != FaceState.Cheeks.NONE) {
            int tint = s.cheeks() == FaceState.Cheeks.BLUSH ? BLUSH : COLD;
            double amount = s.cheeks() == FaceState.Cheeks.BLUSH ? 0.45 : 0.4;
            cheek(out, face, layout, layout.left(), tint, amount);
            cheek(out, face, layout, layout.right(), tint, amount);
        }
        if (s.tear() >= 0) {
            tear(out, face, layout.left(), s.tear());
            tear(out, face, layout.right(), s.tear());
        }
        return out;
    }

    private static void paintEye(int[] out, int[] skin, int[] face, EyeLayout layout, EyeLayout.Eye eye,
                                 EyeState state) {
        int top = eye.top(), bottom = eye.bottom();
        if (state.openness() == EyeState.Openness.HAPPY) {
            // closed lid, and the lash line lifted one row: reads as a smiling "^" at this size. With hair right
            // above the eye there is no room, and the line stays on the eye's top row.
            for (EyeLayout.Pixel p : eye.pixels()) {
                put(out, p.x(), p.y(), layout.lidColor());
            }
            boolean room = top > 0;
            for (int x = eye.minX(); x <= eye.maxX() && room; x++) {
                room = skinLike(face, layout, x, top - 1);
            }
            int row = room ? top - 1 : top;
            for (int x = eye.minX(); x <= eye.maxX(); x++) {
                put(out, x, row, layout.lashColor());
            }
            return;
        }
        if (state.openness() == EyeState.Openness.WIDE && top > 0) {
            int white = whiteOf(face, eye);
            for (EyeLayout.Pixel p : eye.row(top)) {
                if (skinLike(face, layout, p.x(), top - 1)) {
                    put(out, p.x(), top - 1, white);
                }
            }
        }
        int lidRows = switch (state.openness()) {
            case OPEN, WIDE, HAPPY -> 0;
            case HALF -> Math.max(1, eye.height() / 2);
            case CLOSED -> eye.height();
        };
        for (int y = top; y <= bottom; y++) {
            int k = y - top;
            if (k < lidRows) {
                // the last lid row is the lash line; above it, plain lid
                int color = k == lidRows - 1 ? layout.lashColor() : layout.lidColor();
                for (EyeLayout.Pixel p : eye.row(y)) {
                    put(out, p.x(), p.y(), color);
                }
            } else if (state.gaze() != 0 && eye.rowCanGaze(y)) {
                slideIris(out, skin, eye.row(y), state.gaze());
            }
        }
    }

    /** The eye's own white when it has one, otherwise an off-white. */
    private static int whiteOf(int[] face, EyeLayout.Eye eye) {
        for (EyeLayout.Pixel p : eye.pixels()) {
            if (p.sclera()) {
                return Colors.opaque(face[FaceAnalyzer.faceIndex(p.x(), p.y())]);
            }
        }
        return WHITE;
    }

    /**
     * Brow slanting down toward the nose: inner pixel right above the eye, the outer ones a row higher. Drawn on skin
     * only; where hair already covers the forehead the hair stands in for the brow.
     */
    private static void angryBrow(int[] out, int[] face, EyeLayout layout, EyeLayout.Eye eye, boolean leftEye) {
        int color = Colors.mix(layout.skinColor(), 0xFF000000, 0.65);
        int inner = leftEye ? eye.maxX() : eye.minX();
        int top = eye.top();
        for (int x = eye.minX(); x <= eye.maxX(); x++) {
            int y = x == inner || top < 2 ? top - 1 : top - 2;
            if (skinLike(face, layout, x, y)) {
                put(out, x, y, color);
            }
        }
    }

    /** Tints the skin right under the eye. */
    private static void cheek(int[] out, int[] face, EyeLayout layout, EyeLayout.Eye eye, int tint, double amount) {
        int y = eye.bottom() + 1;
        for (int x = eye.minX(); x <= eye.maxX(); x++) {
            if (skinLike(face, layout, x, y)) {
                put(out, x, y, Colors.mix(face[FaceAnalyzer.faceIndex(x, y)], tint, amount));
            }
        }
    }

    /** True for face pixels close to the skin tone: not hair, beard, mask or another feature. */
    private static boolean skinLike(int[] face, EyeLayout layout, int x, int y) {
        return x >= 0 && x < FaceAnalyzer.FACE && y >= 0 && y < FaceAnalyzer.FACE
                && Colors.distance(face[FaceAnalyzer.faceIndex(x, y)], layout.skinColor()) < SKIN_NEAR;
    }

    /** A drop {@code frame} rows below the eye, with a faint wet trail above it. */
    private static void tear(int[] out, int[] face, EyeLayout.Eye eye, int frame) {
        int x = eye.tearColumn();
        int start = eye.bottom() + 1;
        for (int y = start; y <= start + frame && y < FaceAnalyzer.FACE; y++) {
            // read from the already painted copy so the tear goes over a blushing cheek
            int under = Colors.opaque(out[FaceAnalyzer.baseIndex(x, y)]);
            int hat = out[FaceAnalyzer.hatIndex(x, y)];
            if (Colors.a(hat) >= FaceAnalyzer.HAT_OPAQUE) {
                under = Colors.opaque(hat);
            }
            put(out, x, y, Colors.mix(under, TEAR, y == start + frame ? 0.85 : 0.35));
        }
    }

    /**
     * Keeps the order of the iris pixels and of the white pixels, and puts the iris at one end of the row. The base
     * face is shuffled always; the hat layer only when it fully covers the row (eyes drawn on the hat), so glasses
     * or other see-through overlays stay where they are.
     */
    private static void slideIris(int[] out, int[] skin, List<EyeLayout.Pixel> row, int gaze) {
        List<EyeLayout.Pixel> iris = new ArrayList<>(), white = new ArrayList<>();
        for (EyeLayout.Pixel p : row) {
            (p.sclera() ? white : iris).add(p);
        }
        List<EyeLayout.Pixel> order = new ArrayList<>();
        if (gaze < 0) {
            order.addAll(iris);
            order.addAll(white);
        } else {
            order.addAll(white);
            order.addAll(iris);
        }
        boolean hatCovers = row.stream()
                .allMatch(p -> Colors.a(skin[FaceAnalyzer.hatIndex(p.x(), p.y())]) >= FaceAnalyzer.HAT_OPAQUE);
        for (int i = 0; i < row.size(); i++) {
            EyeLayout.Pixel dst = row.get(i), src = order.get(i);
            out[FaceAnalyzer.baseIndex(dst.x(), dst.y())] = skin[FaceAnalyzer.baseIndex(src.x(), src.y())];
            if (hatCovers) {
                out[FaceAnalyzer.hatIndex(dst.x(), dst.y())] = skin[FaceAnalyzer.hatIndex(src.x(), src.y())];
            }
        }
    }

    /** Writes the base face, and the hat layer too where it covers the base, so both layers agree. */
    private static void put(int[] skin, int x, int y, int color) {
        if (x < 0 || x >= FaceAnalyzer.FACE || y < 0 || y >= FaceAnalyzer.FACE) {
            return;
        }
        skin[FaceAnalyzer.baseIndex(x, y)] = Colors.opaque(color);
        int hat = FaceAnalyzer.hatIndex(x, y);
        if (Colors.a(skin[hat]) >= FaceAnalyzer.HAT_OPAQUE) {
            skin[hat] = Colors.opaque(color);
        }
    }
}
