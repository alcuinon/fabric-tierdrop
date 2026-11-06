package net.nian.tierdrop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TierDrop implements ModInitializer {

    public static final String MOD_ID = "tierdrop";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TierDropConfigLoader.loadConfig();
        UseBlockCallback.EVENT.register(this::onVaultOpen);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                TierDropCommand.register(dispatcher));
    }

    private InteractionResult onVaultOpen(Player player, Level world, InteractionHand hand, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos();
        if (!(world.getBlockEntity(pos) instanceof VaultBlockEntity vault)) return InteractionResult.PASS;

        ItemStack held = player.getMainHandItem();
        VaultConfig config = vault.getConfig();
        ItemStack key = config.keyItem();
        VaultState state = vault.getBlockState().getValue(VaultBlock.STATE);
        if (!canBeUnlocked(state) || !isMatchingKey(held, key)) return InteractionResult.PASS;

        double chance = held.getItem() == Items.OMINOUS_TRIAL_KEY ? 1.0 : 0.5;
        VaultRewardHandler.grantVaultReward(player, world, chance);
        return InteractionResult.PASS;
    }

    private static boolean isMatchingKey(ItemStack held, ItemStack key) {
        return ItemStack.isSameItemSameComponents(held, key) && held.getCount() >= key.getCount();
    }

    private static boolean canBeUnlocked(VaultState state) {
        return state != VaultState.INACTIVE
                && state != VaultState.UNLOCKING
                && state != VaultState.EJECTING;
    }
}
