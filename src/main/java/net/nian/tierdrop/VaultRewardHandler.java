package net.nian.tierdrop;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Grants an extra piece of tiered diamond gear when a player opens a vault.
 */
public final class VaultRewardHandler {

    private static final int VAULT_RADIUS = 6;
    private static final Item[] EQUIPMENT = {
            Items.DIAMOND_SWORD,
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS,
            Items.DIAMOND_AXE,
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_SHOVEL
    };

    private VaultRewardHandler() {}

    public static void grantVaultReward(Player player, Level world, double chance) {
        RandomSource random = world.getRandom();
        if (random.nextDouble() > chance) return;

        ItemStack item = damaged(EQUIPMENT[random.nextInt(EQUIPMENT.length)], random);
        if (player instanceof ServerPlayer serverPlayer) {
            TierRoll roll = isNearOminousVault(serverPlayer)
                    ? new TierRoll(3, 20, 5)
                    : new TierRoll(1, 15, -1);
            ItemTierHandler.applyTierIfEligible(item, serverPlayer, roll);
        }

        notify(player, item);

        BlockPos dropPos = player.blockPosition().relative(player.getDirection());
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(new ItemEntity(
                    world, dropPos.getX(), dropPos.getY(), dropPos.getZ(), item));
        }
    }

    private static boolean isNearOminousVault(ServerPlayer player) {
        BlockPos center = player.blockPosition();
        Level world = player.level();
        BlockPos min = center.offset(-VAULT_RADIUS, -VAULT_RADIUS, -VAULT_RADIUS);
        BlockPos max = center.offset(VAULT_RADIUS, VAULT_RADIUS, VAULT_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (!(blockEntity instanceof VaultBlockEntity vault)) continue;

            BlockState state = vault.getBlockState();
            if (state.hasProperty(VaultBlock.OMINOUS) && state.getValue(VaultBlock.OMINOUS)) return true;
        }
        return false;
    }

    private static ItemStack damaged(Item item, RandomSource random) {
        ItemStack stack = new ItemStack(item);
        int max = stack.getMaxDamage();
        int half = max / 2;
        if (half > 0) stack.setDamageValue(random.nextInt(half) + half);
        return stack;
    }

    private static void notify(Player player, ItemStack item) {
        Component message = Component.empty()
                .append(Component.literal("Congratulations! ")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700))))
                .append(Component.literal("You received ")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                .append(item.getHoverName())
                .append(Component.literal(" as an extra Vault reward!")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));
        player.sendSystemMessage(message);
    }
}
