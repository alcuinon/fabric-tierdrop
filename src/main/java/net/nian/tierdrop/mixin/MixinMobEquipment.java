package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.nian.tierdrop.MobTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tiers mob equipment after the mob has been added to the world and its gear is populated.
 */
@Mixin(ServerLevel.class)
public abstract class MixinMobEquipment {

    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && entity instanceof Mob mob) {
            MobTierHandler.process(mob, (ServerLevel) (Object) this);
        }
    }
}
