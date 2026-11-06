package net.nian.tierdrop;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.nian.tierdrop.TierConfig.ItemCategory;
import net.nian.tierdrop.TierConfig.TierEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolls tiers onto naturally equipped hostile mobs, and occasionally promotes one to an elite or a boss.
 */
public final class MobTierHandler {

    private static final int BASE_CHANCE_PERCENT = 10;
    private static final int ROLL_OUT_OF = 150;
    /** Succeeds on 0–5, so 6 in 200 spawns. */
    private static final int ELITE_SPAWN_CHANCE = 200;
    /** Succeeds on 0–1, so 2 in 300 spawns. A boss is not also an elite. */
    private static final int BOSS_SPAWN_CHANCE = 300;
    private static final int MAX_BOSSES_PER_CHUNK = 1;
    private static final int MAX_ELITES_PER_CHUNK = 3;
    private static final String BOSS_TAG = "tierdrop_boss";
    private static final String ELITE_TAG = "tierdrop_elite";

    private MobTierHandler() {}

    public static void process(Mob mob, ServerLevel level) {
        if (!TierDropConfigLoader.equipment().enableMobTiers) return;
        if (mob.getType().getCategory() != MobCategory.MONSTER) return;

        RandomSource random = level.getRandom();
        int difficultyBonus = (int) (level.getCurrentDifficultyAt(mob.blockPosition()).getSpecialMultiplier() * 10);

        boolean bossRoll = random.nextInt(BOSS_SPAWN_CHANCE) <= 1;
        boolean eliteRoll = !bossRoll && random.nextInt(ELITE_SPAWN_CHANCE) <= 5;
        boolean boss = bossRoll && chunkHasRoom(level, mob, BOSS_TAG, MAX_BOSSES_PER_CHUNK);
        boolean elite = eliteRoll && chunkHasRoom(level, mob, ELITE_TAG, MAX_ELITES_PER_CHUNK);

        int appliedLevel = 1;
        int minTierLevel = -5;
        int bonusChance = difficultyBonus;
        if (boss || elite) {
            appliedLevel = 3;
            minTierLevel = boss ? 5 : 2;
            bonusChance = 25 + difficultyBonus;
        }

        if (!tierEquipment(mob, random, appliedLevel, minTierLevel, bonusChance, boss || elite)) return;

        if (boss) {
            mob.addTag(BOSS_TAG);
            mob.setCustomName(bossName(mob));
            buff(mob, 200, 0.35f);
        } else if (elite) {
            mob.addTag(ELITE_TAG);
            mob.setCustomName(eliteName(mob));
            buff(mob, 50, 0.15f);
        }
    }

    /**
     * True when this chunk still has room for another mob of that kind.
     * The search stops at {@code limit} so a crowded chunk is not fully scanned.
     */
    private static boolean chunkHasRoom(ServerLevel level, Mob mob, String tag, int limit) {
        ChunkPos chunk = mob.chunkPosition();
        AABB box = new AABB(
                chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1, level.getMaxY(), chunk.getMaxBlockZ() + 1);
        List<Mob> found = new ArrayList<>(limit);
        level.getEntities(
                EntityTypeTest.forClass(Mob.class),
                box,
                other -> other != mob && other.isAlive() && isKind(other, tag),
                found,
                limit);
        return found.size() < limit;
    }

    private static boolean isKind(Mob mob, String tag) {
        if (mob.entityTags().contains(tag)) return true;
        Component name = mob.getCustomName();
        if (name == null) return false;
        String text = name.getString();
        if (BOSS_TAG.equals(tag)) return text.startsWith("👑");
        return text.startsWith("★ Elite");
    }

    private static boolean tierEquipment(
            Mob mob, RandomSource random, int appliedLevel, int minTierLevel, int bonusChance, boolean guaranteed) {

        boolean tieredAny = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || !ItemTierHandler.shouldApplyTier(stack)) continue;
            if (!guaranteed && random.nextInt(ROLL_OUT_OF) >= BASE_CHANCE_PERCENT + bonusChance) continue;

            EquipmentSlot relevantSlot = ItemTierHandler.getRelevantSlot(stack);
            ItemCategory category = ItemTierHandler.getItemCategory(stack);
            if (relevantSlot == null || category == null) continue;

            TierEffect effect = TierConfig.getRandomTierEffect(
                    random, category, appliedLevel, relevantSlot, bonusChance);
            if (effect == null) continue;

            TierItemEditor.apply(stack, effect, minTierLevel, random);
            mob.setItemSlot(slot, stack);
            tieredAny = true;
        }
        return tieredAny;
    }

    private static Component eliteName(Mob mob) {
        return Component.empty()
                .append(Component.literal("★ Elite ")
                        .withStyle(style -> style.withColor(0xFF4444).withBold(true)))
                .append(mobName(mob, 0xFF9999));
    }

    private static Component bossName(Mob mob) {
        return Component.empty()
                .append(Component.literal("👑 ").withStyle(style -> style.withColor(0xFFD700)))
                .append(Component.literal("Boss ")
                        .withStyle(style -> style.withColor(0xFF2222).withBold(true)))
                .append(mobName(mob, 0xFFAAAA));
    }

    private static Component mobName(Mob mob, int color) {
        return mob.getType().getDescription().copy()
                .withStyle(style -> style.withColor(color).withBold(false));
    }

    private static void buff(Mob mob, double extraHealth, float dropChance) {
        var maxHealth = mob.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(maxHealth.getBaseValue() + extraHealth);
            mob.setHealth((float) maxHealth.getValue());
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, dropChance);
        }
    }
}
