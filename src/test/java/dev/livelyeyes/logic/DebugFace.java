package dev.livelyeyes.logic;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Prints the face grid, the masks and the best bands for the skins whose name contains args[2]. */
public final class DebugFace {
    public static void main(String[] args) throws Exception {
        Map<String, int[]> skins = new LinkedHashMap<>(SkinFiles.vanilla(Path.of(args[0])));
        skins.putAll(SkinFiles.cached(Path.of(args[1])));
        for (Map.Entry<String, int[]> e : skins.entrySet()) {
            if (!e.getKey().contains(args[2])) {
                continue;
            }
            int[] face = FaceAnalyzer.composite(e.getValue());
            int skin = FaceAnalyzer.findSkinTone(face);
            int[] hair = FaceAnalyzer.findHairTones(face, skin);
            boolean[] hairMask = FaceAnalyzer.hairMask(face, hair);
            System.out.printf("== %s skin=%06X hair=%s%n", e.getKey(), skin & 0xFFFFFF, java.util.Arrays.toString(java.util.Arrays.stream(hair).mapToObj(h -> String.format("%06X", h & 0xFFFFFF)).toArray()));
            for (int y = 0; y < 8; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < 8; x++) {
                    int c = face[FaceAnalyzer.faceIndex(x, y)];
                    int i = FaceAnalyzer.faceIndex(x, y);
                    char m = hairMask[i] ? 'H' : Colors.distance(c, skin) > FaceAnalyzer.EYE_DIFF ? '#' : '.';
                    sb.append(String.format("%06X%c%3d ", c & 0xFFFFFF, m, (int) Colors.distance(c, skin)));
                }
                System.out.println(sb);
            }
            boolean[] eye = FaceAnalyzer.eyeMask(face, skin, hairMask);
            for (int top = 1; top <= 6; top++) {
                for (int h = 1; h <= 3; h++) {
                    if (top + h - 1 > 6) {
                        continue;
                    }
                    FaceAnalyzer.Candidate c = FaceAnalyzer.score(face, eye, top, top + h - 1, skin, hair);
                    if (c != null) {
                        System.out.printf("  band %d-%d score %.2f L=%d R=%d%n", top, top + h - 1, c.score(),
                                c.left().size(), c.right().size());
                    }
                }
            }
        }
    }
}
