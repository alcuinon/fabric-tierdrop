package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks ServerLevel.addFreshEntity — fires after the entity is FULLY
 * initialized and added to the world. All subclass finalizeSpawn overrides
 * have completed, all equipment slots are populated.
 *
 * This covers ALL mobs — Zombie, Skeleton, ZombifiedPiglin, Piglin, Pillager, etc.
 */
@Mixin(ServerLevel.class)
public abstract class MixinMobEquipment {

    private static final int BASE_CHANCE_PERCENT = 10;

    // Change to 1 temporarily while testing to force every natural mob to be elite
    private static final int ELITE_SPAWN_CHANCE = 200;
    private static final int BOSS_SPAWN_CHANCE = 300;

    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void onAddFreshEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // Only care about mobs
        if (!(entity instanceof Mob mob)) return;

        ServerLevel serverLevel = (ServerLevel)(Object) this;

        ItemTierHandler.processMobTiers(mob, serverLevel, ELITE_SPAWN_CHANCE, BASE_CHANCE_PERCENT, BOSS_SPAWN_CHANCE);
    }
}