package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies a tier when a new item is inserted into the player's inventory.
 * Higher-quality tiers are applied near Trial Vaults; Ominous Vaults give the best rolls.
 *
 * 26.1 mapping changes:
 *  - PlayerInventory    → Inventory
 *  - ServerPlayerEntity → ServerPlayer
 */
@Mixin(Inventory.class)
public class MixinPlayerInventory {

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void onInsertStack(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.isEmpty() || !ItemTierHandler.shouldApplyTier(stack)) return;
        if(!ItemTierHandler.hasTierAlready(stack) && ItemTierHandler.isWeaponOrArmorOrTool(stack)){
            ItemTierHandler.addClaimedTag(stack);
        }
    }
}
