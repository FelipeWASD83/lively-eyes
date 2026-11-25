package dev.livelyeyes.logic;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FaceAnalyzerTest {
    @Test
    void everyDefaultSkinHasEyesOnRowFourAtTheUsualColumns() throws Exception {
        String jar = System.getProperty("livelyeyes.vanillaJar");
        assumeTrue(jar != null && Files.isRegularFile(Path.of(jar)), "vanilla resources jar not extracted yet");
        Map<String, int[]> vanilla = SkinFiles.vanilla(Path.of(jar));
        assertEquals(18, vanilla.size(), "9 default skins, wide and slim");
        for (Map.Entry<String, int[]> e : vanilla.entrySet()) {
            FaceAnalyzer.Result r = FaceAnalyzer.analyze(e.getValue());
            assertTrue(r.found(), e.getKey() + ": " + r.describe());
            EyeLayout.Eye left = r.layout().left(), right = r.layout().right();
            assertEquals(4, left.top(), e.getKey());
            assertEquals(4, left.bottom(), e.getKey());
            assertEquals(1, left.minX(), e.getKey());
            assertEquals(2, left.maxX(), e.getKey());
            assertEquals(5, right.minX(), e.getKey());
            assertEquals(6, right.maxX(), e.getKey());
            assertTrue(left.canGaze() && right.canGaze(), e.getKey() + " has white and iris side by side");
        }
    }

    @Test
    void plainFaceWithoutFeaturesHasNoEyes() {
        int[] skin = blankSkin(0xFFC89070);
        FaceAnalyzer.Result r = FaceAnalyzer.analyze(skin);
        assertFalse(r.found());
    }

    @Test
    void findsTwoRowEyesUnderBangsAndIgnoresTheMouth() {
        int skinTone = 0xFFF0C8A8, hair = 0xFF5A3A22, iris = 0xFF2E6FB0, white = 0xFFFFFFFF, mouth = 0xFFB05050;
        int[] skin = blankSkin(skinTone);
        for (int x = 0; x < 8; x++) {
            setFace(skin, x, 0, hair);
            setFace(skin, x, 1, hair);
        }
        setFace(skin, 0, 2, hair);
        setFace(skin, 7, 2, hair);
        // eyes at rows 3-4: iris over white on the outer side
        for (int[] p : new int[][]{{1, 3}, {2, 3}, {5, 3}, {6, 3}}) {
            setFace(skin, p[0], p[1], iris);
        }
        setFace(skin, 1, 4, white);
        setFace(skin, 2, 4, iris);
        setFace(skin, 5, 4, iris);
        setFace(skin, 6, 4, white);
        for (int x = 3; x <= 4; x++) {
            setFace(skin, x, 6, mouth);
        }
        FaceAnalyzer.Result r = FaceAnalyzer.analyze(skin);
        assertTrue(r.found(), r.describe());
        assertEquals(3, r.layout().left().top());
        assertEquals(4, r.layout().left().bottom());
        assertTrue(r.layout().left().canHalfClose());
        assertEquals(4, r.layout().right().pixels().size());
    }

    @Test
    void eyesDrawnOnTheHatLayerAreFound() {
        int[] skin = blankSkin(0xFFC89070);
        setHat(skin, 1, 4, 0xFFFFFFFF);
        setHat(skin, 2, 4, 0xFF203060);
        setHat(skin, 5, 4, 0xFF203060);
        setHat(skin, 6, 4, 0xFFFFFFFF);
        FaceAnalyzer.Result r = FaceAnalyzer.analyze(skin);
        assertTrue(r.found(), r.describe());
        assertEquals(4, r.layout().left().top());
    }

    static int[] blankSkin(int tone) {
        int[] skin = new int[64 * 64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                skin[FaceAnalyzer.baseIndex(x, y)] = tone;
            }
        }
        return skin;
    }

    static void setFace(int[] skin, int x, int y, int color) {
        skin[FaceAnalyzer.baseIndex(x, y)] = color;
    }

    static void setHat(int[] skin, int x, int y, int color) {
        skin[FaceAnalyzer.hatIndex(x, y)] = color;
    }
}
