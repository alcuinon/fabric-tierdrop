package net.nian.tierdrop;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.Random;

/**
 * Grants bonus tiered equipment when a player opens a Vault.
 *
 * 26.1 changes:
 *  - ServerWorld → ServerLevel
 *  - Text        → Component
 *  - TextColor.fromRgb() still works (it's been stable)
 *  - ItemEntity constructor is unchanged
 */
public class VaultRewardHandler {

    private static final Random RANDOM = new Random();

    public static void grantVaultReward(Player player, Level world, double chance) {
        if (RANDOM.nextDouble() > chance) return;

        ItemStack item = getRandomEquipment();

        // --- NEW TIER LOGIC START ---
        if (player instanceof ServerPlayer serverPlayer) {
            // Determine tier based on the vault type the player is near
            if (ItemTierHandler.isNearOminousTrialVault(serverPlayer)) {
                // High tier for Ominous (Tier 3-5)
                ItemTierHandler.applyTierIfEligible(item, serverPlayer, 3, 20, 5);
            } else {
                // Standard tier for regular Vaults (Tier 1-3)
                ItemTierHandler.applyTierIfEligible(item, serverPlayer, 1, 15, -1);
            }
        }
        // --- NEW TIER LOGIC END ---

        sendRewardNotification(player, item);

        BlockPos dropPos = player.blockPosition().relative(player.getDirection());
        if (world instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(
                    new ItemEntity(world, dropPos.getX(), dropPos.getY(), dropPos.getZ(), item));
        }
    }

    private static ItemStack getRandomEquipment() {
        // I kept your pool but wrapped them in damaged() here for safety
        ItemStack[] pool = {
                damaged(Items.DIAMOND_SWORD),
                damaged(Items.DIAMOND_HELMET),
                damaged(Items.DIAMOND_CHESTPLATE),
                damaged(Items.DIAMOND_LEGGINGS),
                damaged(Items.DIAMOND_BOOTS),
                damaged(Items.DIAMOND_AXE),
                damaged(Items.DIAMOND_PICKAXE), damaged(Items.DIAMOND_SHOVEL)
        };
        return pool[RANDOM.nextInt(pool.length)].copy(); // Use .copy() to avoid modifying the pool reference
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        int max = stack.getMaxDamage();
        if (max > 0) {
            // Sets durability to 50-100% damaged
            stack.setDamageValue(RANDOM.nextInt(max / 2) + (max / 2));
        }
        return stack;
    }

    private static void sendRewardNotification(Player player, ItemStack item) {
        // Use the HoverName which now includes your Tier prefix!
        Component itemName = item.getHoverName();

        Component msg = Component.empty()
                .append(Component.literal("Congratulations! ")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFD700))))
                .append(Component.literal("You received ")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))))
                .append(itemName) // This will show "Tier III Diamond Sword" in Aqua/Gold
                .append(Component.literal(" as an extra Vault reward!")
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF))));

        player.sendSystemMessage(msg);
    }
}
