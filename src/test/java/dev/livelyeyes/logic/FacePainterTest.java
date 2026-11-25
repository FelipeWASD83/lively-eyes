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
        for (EyeState.Openness a : EyeState.Openness.values()) {
            for (int ga = -1; ga <= 1; ga++) {
                for (EyeState.Openness b : EyeState.Openness.values()) {
                    for (int gb = -1; gb <= 1; gb++) {
                        codes.add(new FaceState(new EyeState(a, ga), new EyeState(b, gb)).code());
                    }
                }
            }
        }
        assertEquals(81, codes.size());
    }
}
