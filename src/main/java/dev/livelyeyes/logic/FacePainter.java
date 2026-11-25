package dev.livelyeyes.logic;

import java.util.ArrayList;
import java.util.List;

/** Paints an expression onto a copy of the skin: closes eyelids and slides irises inside the eye. */
public final class FacePainter {
    private FacePainter() {
    }

    public static int[] paint(int[] skin, EyeLayout layout, FaceState state) {
        int[] out = skin.clone();
        FaceState s = layout.normalize(state);
        paintEye(out, skin, layout, layout.left(), s.left());
        paintEye(out, skin, layout, layout.right(), s.right());
        return out;
    }

    private static void paintEye(int[] out, int[] skin, EyeLayout layout, EyeLayout.Eye eye, EyeState state) {
        int top = eye.top(), bottom = eye.bottom();
        int lidRows = switch (state.openness()) {
            case OPEN -> 0;
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
        skin[FaceAnalyzer.baseIndex(x, y)] = Colors.opaque(color);
        int hat = FaceAnalyzer.hatIndex(x, y);
        if (Colors.a(skin[hat]) >= FaceAnalyzer.HAT_OPAQUE) {
            skin[hat] = Colors.opaque(color);
        }
    }
}
