package net.nian.tierdrop.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a tier to eligible items inside a chest when the chest is opened.
 *
 * 26.1 mapping changes:
 *  - PlayerEntity       → Player
 *  - ServerPlayerEntity → ServerPlayer
 */
@Mixin(ChestBlockEntity.class)
public abstract class MixinChestBlockEntity {

    // Removed the @Shadow level because it's causing the crash.
    // We will use the getter instead.

    @Inject(method = "startOpen", at = @At("HEAD"))
    private void onChestOpened(ContainerUser containerUser, CallbackInfo ci) {
        ChestBlockEntity chest = (ChestBlockEntity)(Object) this;
        Level level = chest.getLevel();

        if (level == null || level.isClientSide()) return;
        if (!(containerUser.getLivingEntity() instanceof ServerPlayer serverPlayer)) return;

        ResourceKey<Level> dimension = level.dimension();

        // Determine loot tier based on dimension
        int appliedLevel;
        int addChance;
        int minTierLevel;

        if (dimension == Level.OVERWORLD) {
            appliedLevel = 1;
            addChance = 0;
            minTierLevel = -3;
        }
        else if (dimension == Level.END) {
            // End chests — best loot
            appliedLevel = 3;
            addChance = 25;
            minTierLevel = 5;
        } else if (dimension == Level.NETHER) {
            // Nether chests — mid-high loot
            appliedLevel = 2;
            addChance = 15;
            minTierLevel = -2;
        } else {
            // Overworld chests — base loot
            appliedLevel = 1;
            addChance = 0;
            minTierLevel = -3;
        }

        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!stack.isEmpty() && ItemTierHandler.shouldApplyTier(stack)) {
                ItemTierHandler.applyTierIfEligible(stack, serverPlayer, appliedLevel, addChance, minTierLevel);
            }
        }
    }
}
