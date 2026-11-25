package dev.livelyeyes.logic;

/**
 * Everything painted on the face in one frame. {@code left} is the eye on the texture's left half (the character's
 * right eye). {@code tear} is -1 for no tears, otherwise a running frame counter that the layout turns into the row
 * the drop has reached.
 */
public record FaceState(EyeState left, EyeState right, Brows brows, Cheeks cheeks, int tear) {
    public enum Brows { NONE, ANGRY }

    public enum Cheeks { NONE, BLUSH, COLD }

    public static final FaceState NEUTRAL = new FaceState(EyeState.NEUTRAL, EyeState.NEUTRAL);

    public FaceState(EyeState left, EyeState right) {
        this(left, right, Brows.NONE, Cheeks.NONE, -1);
    }

    public static FaceState both(EyeState eye) {
        return new FaceState(eye, eye);
    }

    public FaceState withBrows(Brows b) {
        return new FaceState(left, right, b, cheeks, tear);
    }

    public FaceState withCheeks(Cheeks c) {
        return new FaceState(left, right, brows, c, tear);
    }

    public FaceState withTear(int t) {
        return new FaceState(left, right, brows, cheeks, t);
    }

    public boolean isNeutral() {
        return left.equals(EyeState.NEUTRAL) && right.equals(EyeState.NEUTRAL) && brows == Brows.NONE
                && cheeks == Cheeks.NONE && tear < 0;
    }

    /** Unique per normalized state (tear already reduced to a row offset below 8); names the generated textures. */
    public int code() {
        int eyes = left.code() * 15 + right.code();
        int extras = (brows.ordinal() * 3 + cheeks.ordinal()) * 9 + Math.min(8, tear + 1);
        return eyes * 54 + extras;
    }
}
