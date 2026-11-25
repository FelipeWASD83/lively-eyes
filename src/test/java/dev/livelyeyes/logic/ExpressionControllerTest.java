package dev.livelyeyes.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionControllerTest {
    private static final ExpressionController.Settings ALL = ExpressionController.Settings.DEFAULT;
    private static final ExpressionController.Settings STILL = ALL.withBlink(false).withGaze(false);

    @Test
    void blinksRegularlyWithinTheConfiguredInterval() {
        ExpressionController c = new ExpressionController(42, 0);
        ExpressionController.Settings s = ALL.withGaze(false);
        int blinks = 0;
        boolean wasClosed = false;
        long closedMs = 0;
        for (long t = 0; t < 120_000; t += 5) {
            boolean closed = c.update(t, ExpressionController.Inputs.idle(), s).left().openness()
                    == EyeState.Openness.CLOSED;
            if (closed) {
                closedMs += 5;
            }
            if (closed && !wasClosed) {
                blinks++;
            }
            wasClosed = closed;
        }
        // 2 to 6 s apart is 20 to 60 blinks in 2 minutes, plus the odd double blink
        assertTrue(blinks >= 20 && blinks <= 75, "blinks: " + blinks);
        // each blink stays shut about a tenth of a second
        double perBlink = closedMs / (double) blinks;
        assertTrue(perBlink >= 80 && perBlink <= 120, "ms closed per blink: " + perBlink);
    }

    @Test
    void blinkShapeGoesHalfClosedHalf() {
        assertEquals(EyeState.Openness.OPEN, ExpressionController.blinkShape(-1));
        assertEquals(EyeState.Openness.HALF, ExpressionController.blinkShape(10));
        assertEquals(EyeState.Openness.CLOSED, ExpressionController.blinkShape(80));
        assertEquals(EyeState.Openness.HALF, ExpressionController.blinkShape(150));
        assertEquals(EyeState.Openness.OPEN, ExpressionController.blinkShape(200));
    }

    @Test
    void sleepingDeadAndFreshlyHurtKeepTheEyesShut() {
        ExpressionController c = new ExpressionController(1, 0);
        FaceState shut = FaceState.both(EyeState.CLOSED);
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();
        in.sleeping = true;
        assertEquals(shut, c.update(10, in, ALL));
        in = ExpressionController.Inputs.idle();
        in.dead = true;
        assertEquals(shut, c.update(20, in, ALL));
        in = ExpressionController.Inputs.idle();
        in.hurtTime = 9;
        assertEquals(shut, c.update(30, in, ALL));
        in.hurtTime = 2;
        assertEquals(FaceState.NEUTRAL, c.update(40, in, STILL), "the wince ends before the hurt flash does");
    }

    @Test
    void winkClosesOnlyTheTextureLeftEye() {
        ExpressionController c = new ExpressionController(7, 0);
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();
        in.winking = true;
        FaceState f = c.update(100, in, STILL);
        assertEquals(EyeState.CLOSED, f.left());
        assertEquals(EyeState.NEUTRAL, f.right());
    }

    @Test
    void lowHealthHalfClosesAndAGazeTargetWins() {
        ExpressionController c = new ExpressionController(7, 0);
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();
        in.lowHealth = true;
        in.targetGaze = -1;
        FaceState f = c.update(100, in, ALL.withBlink(false));
        assertEquals(new EyeState(EyeState.Openness.HALF, -1), f.left());
        assertEquals(f.left(), f.right());
    }

    @Test
    void idleGlancesHappenAndEnd() {
        ExpressionController c = new ExpressionController(3, 0);
        int glancingMs = 0;
        for (long t = 0; t < 60_000; t += 10) {
            if (c.update(t, ExpressionController.Inputs.idle(), ALL.withBlink(false)).left().gaze() != 0) {
                glancingMs += 10;
            }
        }
        assertTrue(glancingMs > 1000 && glancingMs < 25_000, "ms spent glancing: " + glancingMs);
    }

    @Test
    void eachTriggerGivesItsMood() {
        ExpressionController c = new ExpressionController(5, 0);
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();

        in.fighting = true;
        assertEquals(FaceState.Brows.ANGRY, c.update(100, in, STILL).brows());
        in.fighting = false;

        in.critical = true;
        FaceState crying = c.update(100, in, STILL);
        assertTrue(crying.tear() >= 0);
        assertEquals(EyeState.Openness.HALF, crying.left().openness());
        in.critical = false;

        in.eating = true;
        FaceState happy = c.update(100, in, STILL);
        assertEquals(EyeState.Openness.HAPPY, happy.left().openness());
        assertEquals(FaceState.Cheeks.BLUSH, happy.cheeks());
        in.eating = false;

        in.faceToFace = true;
        in.targetGaze = 1;
        FaceState shy = c.update(100, in, ALL.withBlink(false));
        assertEquals(FaceState.Cheeks.BLUSH, shy.cheeks());
        assertEquals(-1, shy.left().gaze(), "looks away from the one in front");
        in.faceToFace = false;
        in.targetGaze = ExpressionController.NO_TARGET;

        in.startled = true;
        assertEquals(EyeState.Openness.WIDE, c.update(100, in, ALL).left().openness(), "no blink while startled");
        in.startled = false;

        in.freezing = true;
        FaceState cold = c.update(100, in, STILL);
        assertEquals(FaceState.Cheeks.COLD, cold.cheeks());
        assertEquals(EyeState.Openness.HALF, cold.left().openness());
    }

    @Test
    void priorityAndSwitches() {
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();
        in.fighting = true;
        in.critical = true;
        in.startled = true;
        assertEquals(Mood.SURPRISED, ExpressionController.mood(in, ALL));
        in.startled = false;
        assertEquals(Mood.ANGRY, ExpressionController.mood(in, ALL));
        ExpressionController.Settings noAnger = new ExpressionController.Settings(true, 2, 6, true, true, true, true,
                false, true, true, true, true, true);
        assertEquals(Mood.CRYING, ExpressionController.mood(in, noAnger));
        in.manual = Mood.COLD;
        assertEquals(Mood.COLD, ExpressionController.mood(in, ALL), "the menu wins over triggers");
        assertNull(ExpressionController.mood(ExpressionController.Inputs.idle(), ALL));
    }

    @Test
    void tearFramesAdvanceOverTime() {
        ExpressionController c = new ExpressionController(9, 0);
        ExpressionController.Inputs in = ExpressionController.Inputs.idle();
        in.critical = true;
        int a = c.update(1000, in, STILL).tear();
        int b = c.update(1000 + ExpressionController.TEAR_FRAME_MS, in, STILL).tear();
        assertEquals(a + 1, b);
    }
}
