package dev.livelyeyes.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionControllerTest {
    private static final ExpressionController.Settings ALL = ExpressionController.Settings.DEFAULT;

    @Test
    void blinksRegularlyWithinTheConfiguredInterval() {
        ExpressionController c = new ExpressionController(42, 0);
        ExpressionController.Settings s = new ExpressionController.Settings(true, 2, 6, false, true, true, true);
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
        assertEquals(shut, c.update(10, new ExpressionController.Inputs(true, false, 0, false,
                ExpressionController.NO_TARGET, false), ALL));
        assertEquals(shut, c.update(20, new ExpressionController.Inputs(false, true, 0, false,
                ExpressionController.NO_TARGET, false), ALL));
        assertEquals(shut, c.update(30, new ExpressionController.Inputs(false, false, 9, false,
                ExpressionController.NO_TARGET, false), ALL));
        ExpressionController.Settings noBlink = new ExpressionController.Settings(false, 2, 6, false, true, true, true);
        FaceState afterSqueeze = c.update(40, new ExpressionController.Inputs(false, false, 2, false,
                ExpressionController.NO_TARGET, false), noBlink);
        assertEquals(FaceState.NEUTRAL, afterSqueeze, "the wince ends before the hurt flash does");
    }

    @Test
    void winkClosesOnlyTheTextureLeftEye() {
        ExpressionController.Settings noBlink = new ExpressionController.Settings(false, 2, 6, false, true, true, true);
        ExpressionController c = new ExpressionController(7, 0);
        FaceState f = c.update(100, new ExpressionController.Inputs(false, false, 0, false,
                ExpressionController.NO_TARGET, true), noBlink);
        assertEquals(EyeState.CLOSED, f.left());
        assertEquals(EyeState.NEUTRAL, f.right());
    }

    @Test
    void lowHealthHalfClosesAndAGazeTargetWins() {
        ExpressionController.Settings noBlink = new ExpressionController.Settings(false, 2, 6, true, true, true, true);
        ExpressionController c = new ExpressionController(7, 0);
        FaceState f = c.update(100, new ExpressionController.Inputs(false, false, 0, true, -1, false), noBlink);
        assertEquals(new EyeState(EyeState.Openness.HALF, -1), f.left());
        assertEquals(f.left(), f.right());
    }

    @Test
    void idleGlancesHappenAndEnd() {
        ExpressionController.Settings gazeOnly = new ExpressionController.Settings(false, 2, 6, true, true, true, true);
        ExpressionController c = new ExpressionController(3, 0);
        int glancingMs = 0;
        for (long t = 0; t < 60_000; t += 10) {
            if (c.update(t, ExpressionController.Inputs.idle(), gazeOnly).left().gaze() != 0) {
                glancingMs += 10;
            }
        }
        assertTrue(glancingMs > 1000 && glancingMs < 25_000, "ms spent glancing: " + glancingMs);
    }
}
