package dev.livelyeyes.logic;

/** Both eyes in one frame. {@code left} is the eye on the texture's left half (the character's right eye). */
public record FaceState(EyeState left, EyeState right) {
    public static final FaceState NEUTRAL = new FaceState(EyeState.NEUTRAL, EyeState.NEUTRAL);

    public static FaceState both(EyeState eye) {
        return new FaceState(eye, eye);
    }

    public boolean isNeutral() {
        return left.equals(EyeState.NEUTRAL) && right.equals(EyeState.NEUTRAL);
    }

    /** 0..80, unique per state; used to name the generated textures. */
    public int code() {
        return left.code() * 9 + right.code();
    }
}
