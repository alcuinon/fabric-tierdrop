package net.nian.tierdrop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main mod initializer.
 *
 * 26.1 mapping changes:
 *  - ActionResult          → InteractionResult
 *  - Hand                  → InteractionHand
 *  - VaultState (unchanged location in new mappings)
 *  - UseBlockCallback still provided by Fabric API (now using new BlockEvents.USE_ITEM_ON
 *    is preferred, but UseBlockCallback still works in 26.1)
 */
public class TierDrop implements ModInitializer {

    public static final String MOD_ID = "tierdrop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TierDropConfigLoader.loadConfig();
        //TierEffectHandler.register();

        UseBlockCallback.EVENT.register(this::onVaultOpen);

        // Cancel fall damage for players wearing jump-strength tier armour
//        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
//            if (source.getMsgId().equals("fall") && ItemTierHandler.hasJumpStrengthTier(entity)) {
//                return false;
//            }
//            return true;
//        });
    }

    private InteractionResult onVaultOpen(
            Player player, Level world, InteractionHand hand, BlockHitResult hit) {

        if (world.isClientSide()) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        ItemStack mainHand = player.getMainHandItem();

        if (world.getBlockEntity(pos) instanceof VaultBlockEntity vault) {
            VaultConfig config = vault.getConfig();
            ItemStack keyItem = config.keyItem();
            VaultState state = vault.getBlockState().getValue(VaultBlock.STATE);

            if (ItemStack.isSameItemSameComponents(mainHand, keyItem)
                    && mainHand.getCount() >= keyItem.getCount()
                    && canBeUnlocked(state)) {

                double chance = mainHand.getItem() == Items.OMINOUS_TRIAL_KEY ? 1.0 : 0.5;
                VaultRewardHandler.grantVaultReward(player, world, chance);
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean canBeUnlocked(VaultState state) {
        return state != VaultState.INACTIVE
                && state != VaultState.UNLOCKING
                && state != VaultState.EJECTING;
    }
}
