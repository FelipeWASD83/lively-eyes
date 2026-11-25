package dev.livelyeyes.client;

import com.mojang.brigadier.context.CommandContext;
import dev.livelyeyes.logic.EyeLayout;
import dev.livelyeyes.logic.FaceAnalyzer;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Locale;

/** /olhos: says where the eyes were found on your own skin, or why they were not. */
public final class EyesCommand {
    private EyesCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("olhos").executes(EyesCommand::run));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        FaceAnalyzer.Result r = SkinFaces.analysis(mc.player.getSkin().texture());
        Component msg;
        if (r == null) {
            msg = Component.translatable("lively_eyes.command.loading");
        } else if (r.found()) {
            EyeLayout l = r.layout();
            MutableComponent extras = Component.empty();
            if (l.left().canGaze() || l.right().canGaze()) {
                extras.append(Component.translatable("lively_eyes.command.can_gaze"));
            }
            if (l.left().canHalfClose() && l.right().canHalfClose()) {
                extras.append(Component.translatable("lively_eyes.command.can_half"));
            }
            msg = Component.translatable("lively_eyes.command.found",
                    eye(l.left()), eye(l.right()), String.format(Locale.ROOT, "%.2f", l.score()), extras);
        } else {
            String reason = "lively_eyes.failure." + r.failure().name().toLowerCase(Locale.ROOT);
            msg = Component.translatable("lively_eyes.command.not_found", Component.translatable(reason));
        }
        ctx.getSource().sendSuccess(() -> msg, false);
        return r != null && r.found() ? 1 : 0;
    }

    /** Column and row inside the 8x8 face, counted from 1. */
    private static Component eye(EyeLayout.Eye e) {
        String cols = e.minX() == e.maxX() ? String.valueOf(e.minX() + 1) : (e.minX() + 1) + "-" + (e.maxX() + 1);
        String rows = e.top() == e.bottom() ? String.valueOf(e.top() + 1) : (e.top() + 1) + "-" + (e.bottom() + 1);
        return Component.translatable("lively_eyes.command.eye", cols, rows);
    }
}
