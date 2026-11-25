package dev.livelyeyes;

import com.mojang.logging.LogUtils;
import dev.livelyeyes.client.EyesCommand;
import dev.livelyeyes.client.Expressions;
import dev.livelyeyes.client.SkinFaces;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Client only: everything happens in how players are drawn, so servers and other players need nothing. */
@Mod(value = LivelyEyesMod.ID, dist = Dist.CLIENT)
public final class LivelyEyesMod {
    public static final String ID = "lively_eyes";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LivelyEyesMod(IEventBus modBus, ModContainer mod) {
        mod.registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        mod.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(Expressions.WINK);
            e.register(Expressions.MENU);
        });
        // a resource pack can swap the default skins under the same name: forget what was read
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) resources -> SkinFaces.clear()));
        NeoForge.EVENT_BUS.addListener(Expressions::tick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            Expressions.clear();
            SkinFaces.clear();
        });
        NeoForge.EVENT_BUS.addListener(EyesCommand::register);
    }
}
