package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

//@Mixin(targets = "net.minecraft.world.level.block.entity.vault.VaultBlockEntity$Server")
@Mixin(VaultBlockEntity.Server.class)
public class MixinVaultBlockEntity {

    @Inject(
            method = "resolveItemsToEject",
            at = @At("RETURN"), // Inject at the end so we can modify the list
            remap = true
    )
    private static void onResolveVaultLoot(ServerLevel level, VaultConfig config, BlockPos pos, Player player, net.minecraft.world.item.ItemInstance insertedStack, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> items = cir.getReturnValue();

        if (items == null || items.isEmpty() || !(player instanceof ServerPlayer serverPlayer)) return;

        // Check if it's an Ominous Vault (using the config or blockstate)
        // Note: 26.1.1 might use config.overrideLootTableToDisplay() to signal ominousity
        // or check the block state at 'pos'
        boolean isOminous = level.getBlockState(pos).getValue(net.minecraft.world.level.block.VaultBlock.OMINOUS);

        for (ItemStack stack : items) {
            if (!stack.isEmpty() && ItemTierHandler.shouldApplyTier(stack)) {
                if (isOminous) {
                    ItemTierHandler.applyTierIfEligible(stack, serverPlayer, 3, 20, 5);
                } else {
                    ItemTierHandler.applyTierIfEligible(stack, serverPlayer, 3, 15, -2);
                }
            }
        }
    }
}