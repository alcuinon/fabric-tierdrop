package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierDropConfig;
import net.nian.tierdrop.TierDropConfigLoader;
import net.nian.tierdrop.TierItemData;
import net.nian.tierdrop.TierRoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionally tiers crafted gear, then claims it so a later hook does not roll it again.
 */
@Mixin(ResultSlot.class)
public class MixinCraftingSlot {

    @Inject(method = "onTake", at = @At("TAIL"))
    private void onTakeCraftedItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;

        TierDropConfig.TierEquipment equipment = TierDropConfigLoader.equipment();
        if (equipment.applyTiersOnCraftedEquipments && ItemTierHandler.shouldApplyTier(stack)) {
            int level = equipment.allowCraftedEquipmentsToGetHighTiers ? 3 : 1;
            ItemTierHandler.applyTierIfEligible(stack, serverPlayer, new TierRoll(level, 0, -3));
        }

        if (ItemTierHandler.isWeaponOrArmorOrTool(stack)) {
            TierItemData.markClaimed(stack);
        }
    }
}
