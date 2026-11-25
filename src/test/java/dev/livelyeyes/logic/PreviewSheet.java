package dev.livelyeyes.logic;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contact sheet to check detection by eye without opening the game: one line per skin with the face as the game
 * shows it, the eyes found (outlined), and each expression the mod can paint.
 * Args: vanilla resources jar, Prism skin cache folder, output folder.
 */
public final class PreviewSheet {
    private static final int SCALE = 12;
    private static final int CELL = FaceAnalyzer.FACE * SCALE;
    private static final int GAP = 10;
    private static final int LABEL = 230;

    private static final List<Map.Entry<String, FaceState>> COLUMNS = List.of(
            Map.entry("fechado", FaceState.both(EyeState.CLOSED)),
            Map.entry("meio", FaceState.both(EyeState.HALF)),
            Map.entry("olha <", FaceState.both(new EyeState(EyeState.Openness.OPEN, -1))),
            Map.entry("olha >", FaceState.both(new EyeState(EyeState.Openness.OPEN, 1))),
            Map.entry("piscadela", new FaceState(EyeState.CLOSED, EyeState.NEUTRAL)),
            Map.entry("raiva", FaceState.NEUTRAL.withBrows(FaceState.Brows.ANGRY)),
            Map.entry("choro", FaceState.both(EyeState.HALF).withTear(1)),
            Map.entry("vergonha", FaceState.both(new EyeState(EyeState.Openness.OPEN, -1))
                    .withCheeks(FaceState.Cheeks.BLUSH)),
            Map.entry("surpresa", FaceState.both(new EyeState(EyeState.Openness.WIDE, 0))),
            Map.entry("feliz", FaceState.both(new EyeState(EyeState.Openness.HAPPY, 0))
                    .withCheeks(FaceState.Cheeks.BLUSH)),
            Map.entry("frio", FaceState.both(EyeState.HALF).withCheeks(FaceState.Cheeks.COLD)));

    public static void main(String[] args) throws IOException {
        Map<String, int[]> skins = new LinkedHashMap<>(SkinFiles.vanilla(Path.of(args[0])));
        skins.putAll(SkinFiles.cached(Path.of(args[1])));
        for (int i = 3; i < args.length; i++) {
            Path extra = Path.of(args[i]);
            BufferedImage img = ImageIO.read(extra.toFile());
            if (img != null && img.getWidth() == 64 && img.getHeight() == 64) {
                skins.put(extra.getFileName().toString(), SkinFiles.toArgb(img));
            }
        }
        Path out = Path.of(args[2]);
        Files.createDirectories(out);

        int cols = 2 + COLUMNS.size();
        int width = LABEL + cols * (CELL + GAP) + GAP;
        int rowH = CELL + GAP + 18;
        BufferedImage sheet = new BufferedImage(width, 30 + skins.size() * rowH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(0x2B2F36));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.setColor(new Color(0xD8DEE9));
        String[] headers = {"original", "olhos achados"};
        for (int c = 0; c < cols; c++) {
            String h = c < 2 ? headers[c] : COLUMNS.get(c - 2).getKey();
            g.drawString(h, LABEL + GAP + c * (CELL + GAP), 20);
        }

        int found = 0, y = 30;
        for (Map.Entry<String, int[]> e : skins.entrySet()) {
            int[] skin = e.getValue();
            FaceAnalyzer.Result r = FaceAnalyzer.analyze(skin);
            g.setColor(new Color(0xD8DEE9));
            g.drawString(e.getKey(), GAP, y + 20);
            if (r.found()) {
                found++;
                EyeLayout l = r.layout();
                g.drawString(String.format("nota %.2f", l.score()), GAP, y + 38);
                g.drawString("E " + l.left().describe(), GAP, y + 56);
                g.drawString("D " + l.right().describe(), GAP, y + 74);
            } else {
                g.setColor(new Color(0xE0A060));
                g.drawString(r.describe(), GAP, y + 38);
            }
            int x = LABEL + GAP;
            drawFace(g, FaceAnalyzer.composite(skin), x, y);
            x += CELL + GAP;
            drawFace(g, FaceAnalyzer.composite(skin), x, y);
            if (r.found()) {
                outline(g, r.layout().left(), x, y);
                outline(g, r.layout().right(), x, y);
            }
            for (Map.Entry<String, FaceState> col : COLUMNS) {
                x += CELL + GAP;
                if (r.found()) {
                    drawFace(g, FaceAnalyzer.composite(FacePainter.paint(skin, r.layout(), col.getValue())), x, y);
                }
            }
            y += rowH;
        }
        g.dispose();
        Path png = out.resolve("faces.png");
        ImageIO.write(sheet, "png", png.toFile());
        System.out.printf("%d de %d rostos com olhos achados -> %s%n", found, skins.size(), png);
    }

    private static void drawFace(Graphics2D g, int[] face, int x0, int y0) {
        for (int y = 0; y < FaceAnalyzer.FACE; y++) {
            for (int x = 0; x < FaceAnalyzer.FACE; x++) {
                g.setColor(new Color(face[FaceAnalyzer.faceIndex(x, y)], false));
                g.fillRect(x0 + x * SCALE, y0 + y * SCALE, SCALE, SCALE);
            }
        }
    }

    private static void outline(Graphics2D g, EyeLayout.Eye eye, int x0, int y0) {
        g.setStroke(new BasicStroke(2));
        for (EyeLayout.Pixel p : eye.pixels()) {
            g.setColor(p.sclera() ? new Color(0x40C0FF) : new Color(0xFF3060));
            g.drawRect(x0 + p.x() * SCALE + 1, y0 + p.y() * SCALE + 1, SCALE - 2, SCALE - 2);
        }
    }
}
