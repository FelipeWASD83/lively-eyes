package dev.livelyeyes.logic;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Loads skins for tests and previews: the vanilla defaults from the resources jar and whatever Prism cached. */
final class SkinFiles {
    private SkinFiles() {
    }

    static int[] toArgb(BufferedImage img) {
        int[] out = new int[img.getWidth() * img.getHeight()];
        img.getRGB(0, 0, img.getWidth(), img.getHeight(), out, 0, img.getWidth());
        return out;
    }

    static BufferedImage toImage(int[] argb, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, w, h, argb, 0, w);
        return img;
    }

    /** name -> 64x64 ARGB, for every default skin in the vanilla resources jar. */
    static Map<String, int[]> vanilla(Path jar) throws IOException {
        Map<String, int[]> skins = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            List<String> names = new ArrayList<>();
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String n = entries.nextElement().getName();
                if (n.startsWith("assets/minecraft/textures/entity/player/") && n.endsWith(".png")) {
                    names.add(n);
                }
            }
            names.sort(String::compareTo);
            for (String n : names) {
                try (InputStream in = zip.getInputStream(zip.getEntry(n))) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null && img.getWidth() == 64 && img.getHeight() == 64) {
                        String shortName = n.substring("assets/minecraft/textures/entity/player/".length(), n.length() - 4);
                        skins.put(shortName, toArgb(img));
                    }
                }
            }
        }
        return skins;
    }

    /** Skins downloaded by the launcher (files named by hash, no extension). Capes and old 64x32 skins are skipped. */
    static Map<String, int[]> cached(Path dir) throws IOException {
        Map<String, int[]> skins = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            return skins;
        }
        List<Path> files;
        try (var walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (Path f : files) {
            BufferedImage img;
            try {
                img = ImageIO.read(f.toFile());
            } catch (IOException e) {
                continue;
            }
            if (img != null && img.getWidth() == 64 && img.getHeight() == 64) {
                skins.put("cache/" + f.getFileName().toString().substring(0, 8), toArgb(img));
            }
        }
        return skins;
    }
}
