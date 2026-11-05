package net.nian.tierdrop.mixin;


import net.minecraft.server.level.ServerPlayer;
import net.nian.tierdrop.TierParticleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class MixinServerPlayerEntity {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onServerPlayerTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;

        // This logic now runs strictly on the server
        TierParticleHandler.spawnAuraForPlayer(player);
    }
}
