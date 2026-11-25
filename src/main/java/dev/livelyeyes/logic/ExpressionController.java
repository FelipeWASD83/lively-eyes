package dev.livelyeyes.logic;

import java.util.Random;

/**
 * Decides the face of one player frame by frame: random blinks, idle glances, and the reactions to what happens to
 * them (hurt, asleep, dead, low on health, winking). Pure logic, driven by a millisecond clock.
 */
public final class ExpressionController {
    /** Gaze target meaning "nothing interesting nearby". */
    public static final int NO_TARGET = Integer.MIN_VALUE;

    // blink shape, in ms from the start: half-closed, closed, half-closed again
    static final long BLINK_HALF_IN = 35, BLINK_CLOSED_UNTIL = 135, BLINK_END = 175;
    static final long DOUBLE_BLINK_GAP = 260;
    static final double DOUBLE_BLINK_CHANCE = 0.15;
    /** hurtTime counts down from 10 ticks; the eyes stay squeezed shut while it is above this. */
    static final int HURT_SQUEEZE_ABOVE = 4;

    public record Inputs(boolean sleeping, boolean dead, int hurtTime, boolean lowHealth, int targetGaze,
                         boolean winking) {
        public static Inputs idle() {
            return new Inputs(false, false, 0, false, NO_TARGET, false);
        }
    }

    public record Settings(boolean blink, double blinkMinSeconds, double blinkMaxSeconds, boolean gaze,
                           boolean hurt, boolean sleep, boolean tired) {
        public static final Settings DEFAULT = new Settings(true, 2.0, 6.0, true, true, true, true);
    }

    private final Random random;
    private long blinkStart = Long.MIN_VALUE / 2;
    private long nextBlink;
    private boolean doubleBlink;
    private long nextGlance;
    private long glanceEnd;
    private int glanceDir;

    public ExpressionController(long seed, long nowMs) {
        random = new Random(seed);
        // players spawned together should not blink in sync
        nextBlink = nowMs + (long) (random.nextDouble() * 3000);
        nextGlance = nowMs + 2000 + (long) (random.nextDouble() * 6000);
    }

    public FaceState update(long now, Inputs in, Settings s) {
        if (in.dead() || in.sleeping() && s.sleep()) {
            return FaceState.both(EyeState.CLOSED);
        }
        if (s.hurt() && in.hurtTime() > HURT_SQUEEZE_ABOVE) {
            return FaceState.both(EyeState.CLOSED);
        }

        boolean tired = s.tired() && in.lowHealth();
        EyeState.Openness openness = tired ? EyeState.Openness.HALF : EyeState.Openness.OPEN;
        if (s.blink()) {
            if (now >= nextBlink) {
                blinkStart = now;
                doubleBlink = random.nextDouble() < DOUBLE_BLINK_CHANCE;
                double min = Math.max(0.3, s.blinkMinSeconds());
                double max = Math.max(min, s.blinkMaxSeconds());
                double wait = min + random.nextDouble() * (max - min);
                // a tired face blinks twice as often
                nextBlink = now + (long) (wait * 1000 / (tired ? 2 : 1));
            }
            EyeState.Openness blink = blinkShape(now - blinkStart);
            if (blink == EyeState.Openness.OPEN && doubleBlink) {
                blink = blinkShape(now - blinkStart - DOUBLE_BLINK_GAP);
            }
            if (blink.ordinal() > openness.ordinal()) {
                openness = blink;
            }
        }

        int gaze = 0;
        if (s.gaze()) {
            if (in.targetGaze() != NO_TARGET) {
                gaze = in.targetGaze();
            } else {
                if (now >= nextGlance) {
                    glanceDir = random.nextBoolean() ? 1 : -1;
                    glanceEnd = now + 500 + (long) (random.nextDouble() * 900);
                    nextGlance = glanceEnd + 3000 + (long) (random.nextDouble() * 6000);
                }
                gaze = now < glanceEnd ? glanceDir : 0;
            }
        }

        EyeState eye = new EyeState(openness, gaze);
        // a wink closes the character's right eye, which is on the texture's left
        return new FaceState(in.winking() ? EyeState.CLOSED : eye, eye);
    }

    static EyeState.Openness blinkShape(long t) {
        if (t < 0 || t >= BLINK_END) {
            return EyeState.Openness.OPEN;
        }
        return t < BLINK_HALF_IN || t >= BLINK_CLOSED_UNTIL ? EyeState.Openness.HALF : EyeState.Openness.CLOSED;
    }
}
