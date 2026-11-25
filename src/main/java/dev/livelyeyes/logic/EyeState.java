package dev.livelyeyes.logic;

/**
 * How one eye looks in a frame. {@code gaze} is in texture space: -1 moves the iris to the texture's left
 * (the viewer's left when facing the player, which is the character's right), +1 to the other side.
 */
public record EyeState(Openness openness, int gaze) {
    public enum Openness { OPEN, HALF, CLOSED }

    public static final EyeState NEUTRAL = new EyeState(Openness.OPEN, 0);
    public static final EyeState HALF = new EyeState(Openness.HALF, 0);
    public static final EyeState CLOSED = new EyeState(Openness.CLOSED, 0);

    public EyeState {
        gaze = Integer.signum(gaze);
    }

    public EyeState withGaze(int newGaze) {
        return new EyeState(openness, newGaze);
    }

    /** 0..8, unique per state. */
    public int code() {
        return openness.ordinal() * 3 + (gaze + 1);
    }
}
