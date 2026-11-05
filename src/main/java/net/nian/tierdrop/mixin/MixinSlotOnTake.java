package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a tier when a player takes an item from any slot (e.g. loot chests, trades).
 *
 * 26.1 mapping changes:
 *  - PlayerEntity       → Player
 *  - ServerPlayerEntity → ServerPlayer
 */
@Mixin(Slot.class)
public abstract class MixinSlotOnTake {

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (stack.isEmpty() || !ItemTierHandler.shouldApplyTier(stack)) return;

        ItemTierHandler.applyTierIfEligible(stack, serverPlayer, 3, 0, -3);
    }
}
