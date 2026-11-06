package net.nian.tierdrop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.nian.tierdrop.TierConfig.ItemCategory;
import net.nian.tierdrop.TierConfig.TierEffect;

/**
 * Decides which items can roll a tier, then asks {@link TierItemEditor} to write it.
 */
public final class ItemTierHandler {

    private static final int TICKS_PER_DAY = 20 * 60 * 20;
    private static final int BONUS_CHANCE_CAP = 10;

    private ItemTierHandler() {}

    public static boolean isWeapon(ItemStack stack) {
        return stack.has(DataComponents.WEAPON);
    }

    public static boolean isArmor(ItemStack stack) {
        return stack.has(DataComponents.EQUIPPABLE);
    }

    public static boolean isTool(ItemStack stack) {
        return stack.has(DataComponents.TOOL);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem;
    }

    /** Armor made for a player slot. Hand items count; wolf armor and similar gear does not. */
    public static boolean isForPlayer(ItemStack stack) {
        if (!stack.has(DataComponents.EQUIPPABLE)) return true;

        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return false;
        return equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public static boolean isWeaponOrArmorOrTool(ItemStack stack) {
        if (!isForPlayer(stack)) return false;
        return isWeapon(stack) || isArmor(stack) || isTool(stack) || isRangedWeapon(stack);
    }

    public static boolean shouldApplyTier(ItemStack stack) {
        return isWeaponOrArmorOrTool(stack) && !TierItemData.hasTier(stack);
    }

    public static void applyTierIfEligible(ItemStack stack, ServerPlayer player, TierRoll roll) {
        if (!shouldApplyTier(stack)) return;

        ItemCategory category = getItemCategory(stack);
        EquipmentSlot slot = getRelevantSlot(stack);
        if (category == null || slot == null) return;

        RandomSource random = player.getRandom();
        TierEffect effect = TierConfig.getRandomTierEffect(
                random, category, roll.appliedLevel(), slot, bonusChance(player, roll.bonusChance()));
        if (effect == null) return;

        int tierLevel = TierItemEditor.apply(stack, effect, roll.minTierLevel(), random);
        playTierSound(player, tierLevel);
    }

    public static ItemCategory getItemCategory(ItemStack stack) {
        if (isArmor(stack)) return ItemCategory.ARMOR;
        if (isRangedWeapon(stack)) return ItemCategory.RANGE_WEAPON;
        if (isMiningTool(stack)) return ItemCategory.MININGTOOL;
        if (isWeapon(stack)) return ItemCategory.WEAPON;
        if (isTool(stack)) return ItemCategory.MININGTOOL;
        return null;
    }

    public static EquipmentSlot getRelevantSlot(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) return equippable.slot();
        if (isWeapon(stack) || isTool(stack) || isRangedWeapon(stack)) return EquipmentSlot.MAINHAND;
        return null;
    }

    private static boolean isMiningTool(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES);
    }

    /**
     * Playtime adds one point per two in-game days. The total, including the source bonus, is capped at 10.
     */
    private static int bonusChance(ServerPlayer player, int sourceBonus) {
        int daysPlayed = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / TICKS_PER_DAY;
        return Math.min((daysPlayed / 2) + sourceBonus, BONUS_CHANCE_CAP);
    }

    private static void playTierSound(ServerPlayer player, int tierLevel) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (tierLevel >= 9) {
            play(player, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            play(player, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.5f);
            return;
        }

        float pitch = 0.8f + (tierLevel * 0.12f);
        float volume = tierLevel >= 7 ? 1.0f : 0.5f;
        play(player, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, volume, pitch);
    }

    private static void play(ServerPlayer player, double x, double y, double z, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
