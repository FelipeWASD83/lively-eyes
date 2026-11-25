package dev.livelyeyes.logic;

import java.util.Random;

/**
 * Decides the face of one player frame by frame: random blinks, idle glances, the reactions to what happens to them
 * (hurt, asleep, dead, low on health, fighting, on fire or falling, eating, freezing, face to face with someone) and
 * the expression picked by hand. Pure logic, driven by a millisecond clock.
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
    /** How long a tear takes to fall one pixel. */
    static final long TEAR_FRAME_MS = 170;

    /** What the game shows about the player right now. Plain fields: the client fills them every frame. */
    public static final class Inputs {
        public boolean sleeping, dead, lowHealth, critical, winking;
        public boolean fighting, faceToFace, startled, eating, freezing;
        public int hurtTime;
        public int targetGaze = NO_TARGET;
        /** Expression picked by hand (local player only), or null. */
        public Mood manual;

        public static Inputs idle() {
            return new Inputs();
        }
    }

    public record Settings(boolean blink, double blinkMinSeconds, double blinkMaxSeconds, boolean gaze,
                           boolean hurt, boolean sleep, boolean tired, boolean anger, boolean cry, boolean blush,
                           boolean surprise, boolean happy, boolean cold) {
        public static final Settings DEFAULT = new Settings(true, 2.0, 6.0, true, true, true, true,
                true, true, true, true, true, true);

        public Settings withBlink(boolean b) {
            return new Settings(b, blinkMinSeconds, blinkMaxSeconds, gaze, hurt, sleep, tired, anger, cry, blush,
                    surprise, happy, cold);
        }

        public Settings withGaze(boolean g) {
            return new Settings(blink, blinkMinSeconds, blinkMaxSeconds, g, hurt, sleep, tired, anger, cry, blush,
                    surprise, happy, cold);
        }
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

    /** Which mood the triggers call for, highest priority first; null for none. */
    public static Mood mood(Inputs in, Settings s) {
        if (in.manual != null) {
            return in.manual;
        }
        if (in.startled && s.surprise()) {
            return Mood.SURPRISED;
        }
        if (in.fighting && s.anger()) {
            return Mood.ANGRY;
        }
        if (in.critical && s.cry()) {
            return Mood.CRYING;
        }
        if (in.eating && s.happy()) {
            return Mood.HAPPY;
        }
        if (in.faceToFace && s.blush()) {
            return Mood.BLUSHING;
        }
        if (in.freezing && s.cold()) {
            return Mood.COLD;
        }
        return null;
    }

    public FaceState update(long now, Inputs in, Settings s) {
        if (in.dead || in.sleeping && s.sleep()) {
            return FaceState.both(EyeState.CLOSED);
        }
        if (s.hurt() && in.hurtTime > HURT_SQUEEZE_ABOVE && in.manual == null) {
            return FaceState.both(EyeState.CLOSED);
        }

        Mood mood = mood(in, s);
        boolean tired = s.tired() && in.lowHealth;
        EyeState.Openness openness = tired ? EyeState.Openness.HALF : EyeState.Openness.OPEN;
        FaceState.Brows brows = FaceState.Brows.NONE;
        FaceState.Cheeks cheeks = FaceState.Cheeks.NONE;
        int tear = -1;
        boolean canBlink = true, fastBlink = tired;
        if (mood != null) {
            switch (mood) {
                case SURPRISED -> {
                    openness = EyeState.Openness.WIDE;
                    canBlink = false;
                }
                case ANGRY -> brows = FaceState.Brows.ANGRY;
                case CRYING -> {
                    openness = EyeState.Openness.HALF;
                    tear = (int) Math.floorMod(now / TEAR_FRAME_MS, 1_000_000L);
                    fastBlink = true;
                }
                case HAPPY -> {
                    openness = EyeState.Openness.HAPPY;
                    cheeks = FaceState.Cheeks.BLUSH;
                    canBlink = false;
                }
                case BLUSHING -> cheeks = FaceState.Cheeks.BLUSH;
                case COLD -> {
                    openness = EyeState.Openness.HALF;
                    cheeks = FaceState.Cheeks.COLD;
                    fastBlink = true;
                }
            }
        }

        if (s.blink()) {
            if (now >= nextBlink) {
                blinkStart = now;
                doubleBlink = random.nextDouble() < DOUBLE_BLINK_CHANCE;
                double min = Math.max(0.3, s.blinkMinSeconds());
                double max = Math.max(min, s.blinkMaxSeconds());
                double wait = min + random.nextDouble() * (max - min);
                nextBlink = now + (long) (wait * 1000 / (fastBlink ? 2 : 1));
            }
            EyeState.Openness blink = blinkShape(now - blinkStart);
            if (blink == EyeState.Openness.OPEN && doubleBlink) {
                blink = blinkShape(now - blinkStart - DOUBLE_BLINK_GAP);
            }
            if (canBlink && blink.closure() > openness.closure()) {
                openness = blink;
            }
        }

        int gaze = 0;
        if (s.gaze() && mood != Mood.SURPRISED) {
            if (in.targetGaze != NO_TARGET) {
                // a shy face looks away from whoever it is facing
                gaze = mood == Mood.BLUSHING ? -in.targetGaze : in.targetGaze;
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
        EyeState left = in.winking ? EyeState.CLOSED : eye;
        return new FaceState(left, eye, brows, cheeks, tear);
    }

    static EyeState.Openness blinkShape(long t) {
        if (t < 0 || t >= BLINK_END) {
            return EyeState.Openness.OPEN;
        }
        return t < BLINK_HALF_IN || t >= BLINK_CLOSED_UNTIL ? EyeState.Openness.HALF : EyeState.Openness.CLOSED;
    }
}
