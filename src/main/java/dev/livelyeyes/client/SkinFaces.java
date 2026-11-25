package dev.livelyeyes.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.livelyeyes.LivelyEyesMod;
import dev.livelyeyes.logic.FaceAnalyzer;
import dev.livelyeyes.logic.FacePainter;
import dev.livelyeyes.logic.FaceState;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per skin texture: reads the pixels back from the GPU once, finds the eyes, and builds one small texture per
 * expression the first time it is needed. Render thread only.
 */
public final class SkinFaces {
    private static final int MAX_SKINS = 64;
    private static final long RETRY_MS = 1000;

    private static final class Entry {
        final int id;
        final int[] skin;
        final FaceAnalyzer.Result result;
        final Map<Integer, ResourceLocation> variants = new HashMap<>();

        Entry(int id, int[] skin, FaceAnalyzer.Result result) {
            this.id = id;
            this.skin = skin;
            this.result = result;
        }
    }

    private static final LinkedHashMap<ResourceLocation, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    private static final Map<ResourceLocation, Long> NOT_READY = new HashMap<>();
    private static int nextId;

    private SkinFaces() {
    }

    /** The texture to draw for this skin with this face; the skin itself when nothing changes or eyes are unknown. */
    public static ResourceLocation variant(ResourceLocation skin, FaceState state) {
        Entry e = entry(skin);
        if (e == null || !e.result.found()) {
            return skin;
        }
        FaceState normalized = e.result.layout().normalize(state);
        if (normalized.isNeutral()) {
            return skin;
        }
        ResourceLocation ready = e.variants.get(normalized.code());
        if (ready != null) {
            return ready;
        }
        if (!RenderSystem.isOnRenderThread()) {
            return skin; // textures are only created on the render thread
        }
        ResourceLocation built = build(e, normalized);
        e.variants.put(normalized.code(), built);
        return built;
    }

    /** Detection result for a skin, or null while the texture is not on the GPU yet. */
    public static FaceAnalyzer.Result analysis(ResourceLocation skin) {
        Entry e = entry(skin);
        return e == null ? null : e.result;
    }

    public static void clear() {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        for (Entry e : CACHE.values()) {
            e.variants.values().forEach(textures::release);
        }
        CACHE.clear();
        NOT_READY.clear();
    }

    private static Entry entry(ResourceLocation skin) {
        Entry e = CACHE.get(skin);
        if (e != null || !RenderSystem.isOnRenderThread()) {
            return e;
        }
        long now = Util.getMillis();
        Long last = NOT_READY.get(skin);
        if (last != null && now - last < RETRY_MS) {
            return null;
        }
        int[] pixels;
        FaceAnalyzer.Result result;
        try {
            pixels = readBack(skin);
        } catch (RuntimeException ex) {
            LivelyEyesMod.LOGGER.warn("Could not read skin {}: {}", skin, ex.toString());
            pixels = null;
        }
        if (pixels == null) {
            NOT_READY.put(skin, now);
            return null;
        }
        NOT_READY.remove(skin);
        result = pixels.length == 0
                ? new FaceAnalyzer.Result(null, FaceAnalyzer.Failure.NOT_64X64, 0)
                : FaceAnalyzer.analyze(pixels);
        e = new Entry(nextId++, pixels, result);
        CACHE.put(skin, e);
        LivelyEyesMod.LOGGER.debug("Eyes on {}: {}", skin, result.describe());
        evictOld();
        return e;
    }

    private static void evictOld() {
        if (CACHE.size() <= MAX_SKINS) {
            return;
        }
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        List<ResourceLocation> drop = new ArrayList<>(CACHE.keySet()).subList(0, CACHE.size() - MAX_SKINS);
        for (ResourceLocation loc : List.copyOf(drop)) {
            CACHE.remove(loc).variants.values().forEach(textures::release);
        }
    }

    /**
     * Pixels of a loaded texture as ARGB. Null when the texture is not uploaded yet, an empty array when it is not
     * a 64x64 skin (HD skins are left alone).
     */
    private static int[] readBack(ResourceLocation skin) {
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin, null);
        if (texture == null) {
            return null;
        }
        int previous = GlStateManager._getInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._bindTexture(texture.getId());
            int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w == 0 || h == 0) {
                return null;
            }
            if (w != FaceAnalyzer.SKIN_SIZE || h != FaceAnalyzer.SKIN_SIZE) {
                return new int[0];
            }
            try (NativeImage image = new NativeImage(w, h, false)) {
                image.downloadTexture(0, false);
                int[] argb = new int[w * h];
                boolean anything = false;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int c = abgrToArgb(image.getPixelRGBA(x, y));
                        argb[y * w + x] = c;
                        anything |= c != 0;
                    }
                }
                return anything ? argb : null;
            }
        } finally {
            GlStateManager._bindTexture(previous);
        }
    }

    private static ResourceLocation build(Entry e, FaceState state) {
        int[] painted = FacePainter.paint(e.skin, e.result.layout(), state);
        NativeImage image = new NativeImage(FaceAnalyzer.SKIN_SIZE, FaceAnalyzer.SKIN_SIZE, false);
        for (int y = 0; y < FaceAnalyzer.SKIN_SIZE; y++) {
            for (int x = 0; x < FaceAnalyzer.SKIN_SIZE; x++) {
                image.setPixelRGBA(x, y, abgrToArgb(painted[y * FaceAnalyzer.SKIN_SIZE + x]));
            }
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(LivelyEyesMod.ID, "face/" + e.id + "/" + state.code());
        Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(image));
        return loc;
    }

    /** Swaps red and blue; the same swap goes both ways between NativeImage's ABGR and ARGB. */
    static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
    }
}
