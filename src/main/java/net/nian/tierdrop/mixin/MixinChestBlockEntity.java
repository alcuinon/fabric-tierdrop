package net.nian.tierdrop.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierRoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tiers eligible gear already inside a chest when a player opens it.
 */
@Mixin(ChestBlockEntity.class)
public abstract class MixinChestBlockEntity {

    @Inject(method = "startOpen", at = @At("HEAD"))
    private void onChestOpened(ContainerUser containerUser, CallbackInfo ci) {
        ChestBlockEntity chest = (ChestBlockEntity) (Object) this;
        Level level = chest.getLevel();
        if (level == null || level.isClientSide()) return;
        if (!(containerUser.getLivingEntity() instanceof ServerPlayer serverPlayer)) return;

        TierRoll roll = rollFor(level.dimension());
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty() && ItemTierHandler.shouldApplyTier(stack)) {
                ItemTierHandler.applyTierIfEligible(stack, serverPlayer, roll);
            }
        }
    }

    private static TierRoll rollFor(ResourceKey<Level> dimension) {
        if (dimension == Level.END) return new TierRoll(3, 25, 5);
        if (dimension == Level.NETHER) return new TierRoll(2, 15, -2);
        return new TierRoll(1, 0, -3);
    }
}
