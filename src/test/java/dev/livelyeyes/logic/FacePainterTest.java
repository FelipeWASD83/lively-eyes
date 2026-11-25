package dev.livelyeyes.logic;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FacePainterTest {
    private static final int TONE = 0xFFC89070, WHITE = 0xFFFFFFFF, IRIS = 0xFF203060;

    /** Steve-like face: white outside, iris inside, row 4. */
    private static int[] steveLike() {
        int[] skin = FaceAnalyzerTest.blankSkin(TONE);
        FaceAnalyzerTest.setFace(skin, 1, 4, WHITE);
        FaceAnalyzerTest.setFace(skin, 2, 4, IRIS);
        FaceAnalyzerTest.setFace(skin, 5, 4, IRIS);
        FaceAnalyzerTest.setFace(skin, 6, 4, WHITE);
        return skin;
    }

    private static EyeLayout layout(int[] skin) {
        FaceAnalyzer.Result r = FaceAnalyzer.analyze(skin);
        assertTrue(r.found(), r.describe());
        return r.layout();
    }

    @Test
    void neutralChangesNothing() {
        int[] skin = steveLike();
        assertArrayEquals(skin, FacePainter.paint(skin, layout(skin), FaceState.NEUTRAL));
    }

    @Test
    void closedTouchesOnlyEyePixelsAndHidesTheWhite() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        int[] out = FacePainter.paint(skin, l, FaceState.both(EyeState.CLOSED));
        Set<Integer> eye = new HashSet<>();
        for (EyeLayout.Eye e : new EyeLayout.Eye[]{l.left(), l.right()}) {
            for (EyeLayout.Pixel p : e.pixels()) {
                eye.add(FaceAnalyzer.baseIndex(p.x(), p.y()));
                assertEquals(l.lashColor(), out[FaceAnalyzer.baseIndex(p.x(), p.y())]);
            }
        }
        for (int i = 0; i < skin.length; i++) {
            if (!eye.contains(i)) {
                assertEquals(skin[i], out[i], "pixel " + i + " is not part of an eye");
            }
        }
        assertNotEquals(WHITE, l.lashColor());
    }

    @Test
    void gazeSlidesTheIrisAndKeepsTheColors() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        int[] left = FacePainter.paint(skin, l, FaceState.both(new EyeState(EyeState.Openness.OPEN, -1)));
        assertEquals(IRIS, left[FaceAnalyzer.baseIndex(1, 4)]);
        assertEquals(WHITE, left[FaceAnalyzer.baseIndex(2, 4)]);
        assertEquals(IRIS, left[FaceAnalyzer.baseIndex(5, 4)]);
        assertEquals(WHITE, left[FaceAnalyzer.baseIndex(6, 4)]);
        int[] right = FacePainter.paint(skin, l, FaceState.both(new EyeState(EyeState.Openness.OPEN, 1)));
        assertEquals(WHITE, right[FaceAnalyzer.baseIndex(1, 4)]);
        assertEquals(IRIS, right[FaceAnalyzer.baseIndex(2, 4)]);
        assertEquals(WHITE, right[FaceAnalyzer.baseIndex(5, 4)]);
        assertEquals(IRIS, right[FaceAnalyzer.baseIndex(6, 4)]);
    }

    @Test
    void halfOnAOneRowEyeIsTheSameAsOpen() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        assertTrue(l.normalize(FaceState.both(EyeState.HALF)).isNeutral());
    }

    @Test
    void eyesOnTheHatAreClosedOnTheHatToo() {
        int[] skin = FaceAnalyzerTest.blankSkin(TONE);
        FaceAnalyzerTest.setHat(skin, 1, 4, WHITE);
        FaceAnalyzerTest.setHat(skin, 2, 4, IRIS);
        FaceAnalyzerTest.setHat(skin, 5, 4, IRIS);
        FaceAnalyzerTest.setHat(skin, 6, 4, WHITE);
        EyeLayout l = layout(skin);
        int[] out = FacePainter.paint(skin, l, FaceState.both(EyeState.CLOSED));
        assertEquals(l.lashColor(), out[FaceAnalyzer.hatIndex(1, 4)]);
        assertEquals(l.lashColor(), out[FaceAnalyzer.baseIndex(1, 4)]);
    }

    @Test
    void faceStateCodesAreUnique() {
        Set<Integer> codes = new HashSet<>();
        int count = 0;
        for (EyeState.Openness a : EyeState.Openness.values()) {
            for (int ga = -1; ga <= 1; ga++) {
                for (EyeState.Openness b : EyeState.Openness.values()) {
                    for (int gb = -1; gb <= 1; gb++) {
                        for (FaceState.Brows br : FaceState.Brows.values()) {
                            for (FaceState.Cheeks ch : FaceState.Cheeks.values()) {
                                for (int tear = -1; tear < 7; tear++) {
                                    codes.add(new FaceState(new EyeState(a, ga), new EyeState(b, gb), br, ch, tear)
                                            .code());
                                    count++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(count, codes.size());
    }

    @Test
    void angryBrowsSlantTowardTheNose() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        int[] out = FacePainter.paint(skin, l, FaceState.NEUTRAL.withBrows(FaceState.Brows.ANGRY));
        // eyes at x 1-2 and 5-6, row 4: inner brow pixels on row 3, outer ones on row 2
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(2, 3)]);
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(1, 2)]);
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(5, 3)]);
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(6, 2)]);
        assertEquals(TONE, out[FaceAnalyzer.baseIndex(1, 3)]);
        assertEquals(TONE, out[FaceAnalyzer.baseIndex(6, 3)]);
        assertEquals(WHITE, out[FaceAnalyzer.baseIndex(1, 4)], "the eye itself stays open");
    }

    @Test
    void blushTintsOnlySkinUnderTheEyes() {
        int[] skin = steveLike();
        int beard = 0xFF3A2410;
        FaceAnalyzerTest.setFace(skin, 6, 5, beard);
        EyeLayout l = layout(skin);
        int[] out = FacePainter.paint(skin, l, FaceState.NEUTRAL.withCheeks(FaceState.Cheeks.BLUSH));
        int cheek = out[FaceAnalyzer.baseIndex(1, 5)];
        assertTrue(Colors.r(cheek) > Colors.r(TONE) || Colors.b(cheek) > Colors.b(TONE), "pinker than skin");
        assertNotEquals(TONE, cheek);
        assertEquals(beard, out[FaceAnalyzer.baseIndex(6, 5)], "beard is not skin and stays");
        assertEquals(TONE, out[FaceAnalyzer.baseIndex(3, 5)], "between the eyes nothing changes");
    }

    @Test
    void tearFallsDownTheIrisColumnAndWraps() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        // eye bottom is row 4, so a tear has rows 5, 6 and 7: frame 4 wraps to frame 1 (row 6)
        int[] out = FacePainter.paint(skin, l, FaceState.NEUTRAL.withTear(4));
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(2, 6)], "drop on the iris column of the left eye");
        assertNotEquals(TONE, out[FaceAnalyzer.baseIndex(5, 6)], "and of the right eye");
        assertEquals(TONE, out[FaceAnalyzer.baseIndex(2, 7)], "not past the drop yet");
        assertTrue(Colors.b(out[FaceAnalyzer.baseIndex(2, 6)]) > Colors.b(TONE), "bluish");
    }

    @Test
    void wideAddsWhiteAboveAndHappyLiftsTheLashLine() {
        int[] skin = steveLike();
        EyeLayout l = layout(skin);
        int[] wide = FacePainter.paint(skin, l, FaceState.both(new EyeState(EyeState.Openness.WIDE, 0)));
        assertEquals(WHITE, wide[FaceAnalyzer.baseIndex(1, 3)]);
        assertEquals(WHITE, wide[FaceAnalyzer.baseIndex(2, 3)]);
        int[] happy = FacePainter.paint(skin, l, FaceState.both(new EyeState(EyeState.Openness.HAPPY, 0)));
        assertEquals(l.lashColor(), happy[FaceAnalyzer.baseIndex(1, 3)]);
        assertEquals(l.lidColor(), happy[FaceAnalyzer.baseIndex(1, 4)]);
    }
}
