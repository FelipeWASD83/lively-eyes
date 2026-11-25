package dev.livelyeyes.mixin;

import dev.livelyeyes.client.Expressions;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The player model takes its texture from here (body, hat layer, and the 3D layers of Skin Layers 3D, which draw
 * into the same buffer). Swapping it for a copy with the eyes painted is the whole trick. First-person arms read the
 * skin elsewhere and keep the original, which has the same arms anyway.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "getTextureLocation(Lnet/minecraft/client/player/AbstractClientPlayer;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void livelyEyes$expression(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation original = cir.getReturnValue();
        // another mod already swapped the texture (ETF does it for skins with its own features): leave it alone
        if (original == null || !original.equals(player.getSkin().texture())) {
            return;
        }
        ResourceLocation face = Expressions.textureFor(player, original);
        if (face != original) {
            cir.setReturnValue(face);
        }
    }
}
