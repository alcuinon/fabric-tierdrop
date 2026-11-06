package net.nian.tierdrop.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierItemData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Claims gear as it enters the inventory so a later chest open cannot roll the player's own items.
 */
@Mixin(Inventory.class)
public class MixinPlayerInventory {

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"))
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !ItemTierHandler.shouldApplyTier(stack)) return;
        TierItemData.markClaimed(stack);
    }
}
