package net.nian.tierdrop.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierDropConfigLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionally applies a tier when a player picks up a crafted item.
 * Also marks crafted weapons/armor/tools as "claimed" so they don't
 * get re-tiered later (unless config allows it).
 *
 * 26.1 mapping changes:
 *  - CraftingResultSlot   → ResultSlot
 *  - PlayerEntity         → Player
 *  - ServerPlayerEntity   → ServerPlayer
 *  - NbtComponent         → CustomData
 *  - DataComponentTypes   → DataComponents
 */
@Mixin(ResultSlot.class)
public class MixinCraftingSlot {

    @Inject(method = "onTake", at = @At("TAIL"))
    private void onTakeCraftedItem(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (stack.isEmpty()) return;

        // Optionally apply a tier on craft
        if (TierDropConfigLoader.config.TierEquipment.ApplyTiersOnCraftedEquipments
                && ItemTierHandler.shouldApplyTier(stack)) {

            int level = TierDropConfigLoader.config.TierEquipment.AllowCraftedEquipmentsToGetHighTiers ? 3 : 1;
            ItemTierHandler.applyTierIfEligible(stack, serverPlayer, level, 0, -3);
        }

        // Mark all crafted weapons/armor/tools as claimed so they aren't tiered again
        if (ItemTierHandler.isWeaponOrArmorOrTool(stack)) {
            CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CustomData updated = current.update(nbt -> {
                CompoundTag origin = new CompoundTag();
                origin.putString("tierDrop", "claimed");
                nbt.put("tierdrop:origin", origin);
            });
            stack.set(DataComponents.CUSTOM_DATA, updated);
        }
    }
}
