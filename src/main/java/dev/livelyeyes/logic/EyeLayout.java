package dev.livelyeyes.logic;

import java.util.List;

/**
 * Where the eyes are on a skin's face, in face coordinates (0..7 on both axes, as drawn on the front of the head),
 * plus the colors used to close them.
 */
public record EyeLayout(Eye left, Eye right, int skinColor, int lidColor, int lashColor, double score) {

    /** One face pixel that belongs to an eye. {@code sclera} marks the white of the eye. */
    public record Pixel(int x, int y, boolean sclera) {
    }

    public record Eye(List<Pixel> pixels) {
        public Eye {
            pixels = List.copyOf(pixels);
        }

        public int top() {
            return pixels.stream().mapToInt(Pixel::y).min().orElse(0);
        }

        public int bottom() {
            return pixels.stream().mapToInt(Pixel::y).max().orElse(0);
        }

        public int minX() {
            return pixels.stream().mapToInt(Pixel::x).min().orElse(0);
        }

        public int maxX() {
            return pixels.stream().mapToInt(Pixel::x).max().orElse(0);
        }

        public int height() {
            return bottom() - top() + 1;
        }

        /** Pixels of one row, sorted by x. */
        public List<Pixel> row(int y) {
            return pixels.stream().filter(p -> p.y() == y).sorted((a, b) -> Integer.compare(a.x(), b.x())).toList();
        }

        /** A row can move its iris when it is one unbroken run holding both white and iris pixels. */
        public boolean rowCanGaze(int y) {
            List<Pixel> row = row(y);
            if (row.size() < 2 || row.get(row.size() - 1).x() - row.get(0).x() != row.size() - 1) {
                return false;
            }
            boolean white = row.stream().anyMatch(Pixel::sclera);
            boolean iris = row.stream().anyMatch(p -> !p.sclera());
            return white && iris;
        }

        public boolean canGaze() {
            for (int y = top(); y <= bottom(); y++) {
                if (rowCanGaze(y)) {
                    return true;
                }
            }
            return false;
        }

        public boolean canHalfClose() {
            return height() >= 2;
        }

        /** Drops what this eye cannot show, so states that would look the same share one texture. */
        public EyeState normalize(EyeState state) {
            EyeState.Openness o = state.openness();
            if (o == EyeState.Openness.HALF && !canHalfClose() || o == EyeState.Openness.WIDE && top() == 0) {
                o = EyeState.Openness.OPEN;
            }
            boolean shut = o == EyeState.Openness.CLOSED || o == EyeState.Openness.HAPPY;
            int gaze = shut || !canGaze() ? 0 : state.gaze();
            return new EyeState(o, gaze);
        }

        /** The column a tear runs down: the iris pixel nearest the middle of the face, or the eye's middle. */
        public int tearColumn() {
            int best = -1;
            for (Pixel p : pixels) {
                if (!p.sclera() && (best < 0 || Math.abs(p.x() * 2 - 7) < Math.abs(best * 2 - 7))) {
                    best = p.x();
                }
            }
            return best >= 0 ? best : (minX() + maxX()) / 2;
        }

        public String describe() {
            String cols = minX() == maxX() ? String.valueOf(minX()) : minX() + "-" + maxX();
            String rows = top() == bottom() ? String.valueOf(top()) : top() + "-" + bottom();
            return "x " + cols + ", y " + rows;
        }
    }

    public FaceState normalize(FaceState state) {
        FaceState.Brows brows = state.brows() == FaceState.Brows.ANGRY && Math.min(left.top(), right.top()) >= 1
                ? FaceState.Brows.ANGRY : FaceState.Brows.NONE;
        int lowest = Math.max(left.bottom(), right.bottom());
        FaceState.Cheeks cheeks = lowest < FaceAnalyzer.FACE - 1 ? state.cheeks() : FaceState.Cheeks.NONE;
        int room = FaceAnalyzer.FACE - 1 - lowest;
        int tear = state.tear() >= 0 && room > 0 ? state.tear() % room : -1;
        return new FaceState(left.normalize(state.left()), right.normalize(state.right()), brows, cheeks, tear);
    }
}
