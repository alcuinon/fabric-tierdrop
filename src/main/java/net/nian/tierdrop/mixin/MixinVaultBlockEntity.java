package net.nian.tierdrop.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierRoll;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(VaultBlockEntity.Server.class)
public class MixinVaultBlockEntity {

    @Inject(method = "resolveItemsToEject", at = @At("RETURN"))
    private static void onResolveVaultLoot(
            ServerLevel level, VaultConfig config, BlockPos pos, Player player,
            net.minecraft.world.item.ItemInstance insertedStack,
            CallbackInfoReturnable<List<ItemStack>> cir) {

        List<ItemStack> items = cir.getReturnValue();
        if (items == null || items.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) return;

        boolean ominous = level.getBlockState(pos).getValue(VaultBlock.OMINOUS);
        TierRoll roll = ominous ? new TierRoll(3, 20, 5) : new TierRoll(3, 15, -2);
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && ItemTierHandler.shouldApplyTier(stack)) {
                ItemTierHandler.applyTierIfEligible(stack, serverPlayer, roll);
            }
        }
    }
}
