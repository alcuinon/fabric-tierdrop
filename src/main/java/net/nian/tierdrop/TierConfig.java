package net.nian.tierdrop;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tier pools built from {@code tierdrop_config.json}.
 */
public final class TierConfig {

    private static final int LOW_WEIGHT_CAP = 20;

    public enum ItemCategory {
        ARMOR, WEAPON, MININGTOOL, RANGE_WEAPON
    }

    public enum Tier {
        COMMON, UNCOMMON, RARE, EPIC, UNIQUE, LEGENDARY
    }

    public static final class TierEffect {
        public final String prefixName;
        public final TextColor color;
        public final int[] chances;

        private final Map<EquipmentSlot, Map<Holder<Attribute>, MinMaxDouble>> slotAttributes =
                new EnumMap<>(EquipmentSlot.class);
        private final List<MobEffectInstance> statusEffects = new ArrayList<>();
        private final List<TierDropConfig.TierParticleConfig> particles = new ArrayList<>();

        private String statusEffectTrigger = "";
        private int statusEffectTriggerChance = 30;
        private int statusEffectDuration = 5;
        private String statusEffectTarget = "";
        private String statusEffectName = "";
        private String statusEffectDescription = "";

        public TierEffect(String prefixName, TextColor color, int... chances) {
            this.prefixName = prefixName;
            this.color = color;
            this.chances = chances;
        }

        public void addAttribute(EquipmentSlot slot, Holder<Attribute> attribute, double min, double max) {
            if (attribute == null || slot == null) return;
            slotAttributes.computeIfAbsent(slot, ignored -> new HashMap<>()).put(attribute, new MinMaxDouble(min, max));
        }

        public void addParticle(TierDropConfig.TierParticleConfig config) {
            if (config != null) particles.add(config);
        }

        public void addStatusEffect(Holder<MobEffect> effect, int level) {
            statusEffects.add(new MobEffectInstance(effect, Integer.MAX_VALUE, level - 1, true, false));
        }

        public void addStatusEffectTrigger(String value) { statusEffectTrigger = value; }
        public void addStatusEffectTarget(String value) { statusEffectTarget = value; }
        public void addStatusEffectTriggerChance(int value) { statusEffectTriggerChance = value; }
        public void addStatusEffectDuration(int value) { statusEffectDuration = value; }
        public void addStatusEffectName(String value) { statusEffectName = value; }
        public void addStatusEffectDescription(String value) { statusEffectDescription = value; }

        public Map<Holder<Attribute>, MinMaxDouble> getAttributesForSlot(EquipmentSlot slot) {
            return slotAttributes.getOrDefault(slot, Collections.emptyMap());
        }

        public List<MobEffectInstance> getStatusEffects() { return statusEffects; }
        public String getStatusEffectName() { return statusEffectName; }
        public String getStatusEffectTrigger() { return statusEffectTrigger; }
        public String getStatusEffectTarget() { return statusEffectTarget; }
        public int getStatusEffectTriggerChance() { return statusEffectTriggerChance; }
        public int getStatusEffectDuration() { return statusEffectDuration; }
        public String getStatusEffectDescription() { return statusEffectDescription; }
        public List<TierDropConfig.TierParticleConfig> getParticles() { return particles; }

        public boolean isCompatibleSlot(EquipmentSlot slot) {
            Map<Holder<Attribute>, MinMaxDouble> attributes = slotAttributes.get(slot);
            return attributes != null && !attributes.isEmpty();
        }

        public int chanceAt(int index) {
            if (chances == null || chances.length == 0 || index < 0) return 0;
            return chances[Math.min(index, chances.length - 1)];
        }
    }

    public static final EnumMap<ItemCategory, EnumMap<Tier, List<TierEffect>>> CONFIG = new EnumMap<>(ItemCategory.class);

    /** Rebuilds the pools from the config that was just loaded. */
    public static void reload() {
        TierDropConfig.TierEquipment equipment = TierDropConfigLoader.equipment();
        CONFIG.clear();
        CONFIG.put(ItemCategory.ARMOR, tiersFor(equipment.armor));
        CONFIG.put(ItemCategory.WEAPON, tiersFor(equipment.weapon));
        CONFIG.put(ItemCategory.MININGTOOL, tiersFor(equipment.miningTool));
        CONFIG.put(ItemCategory.RANGE_WEAPON, tiersFor(equipment.rangeWeapon));
    }

    private TierConfig() {}

    public static TierEffect getRandomTierEffect(
            RandomSource random, ItemCategory category, int appliedLevel,
            EquipmentSlot slot, int additionalChance) {

        if (category == null) return null;

        Map<Tier, List<TierEffect>> effectsByTier = CONFIG.get(category);
        if (effectsByTier == null) return null;

        int chanceIndex = Math.max(0, appliedLevel - 1);
        List<TierEffect> compatible = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();

        for (Tier tier : Tier.values()) {
            List<TierEffect> effects = effectsByTier.get(tier);
            if (effects == null) continue;
            for (TierEffect effect : effects) {
                if (effect == null || !effect.isCompatibleSlot(slot)) continue;
                int weight = weightFor(effect, chanceIndex, additionalChance);
                if (weight > 0) {
                    compatible.add(effect);
                    weights.add(weight);
                }
            }
        }

        if (compatible.isEmpty()) return firstCompatible(effectsByTier.get(Tier.COMMON), slot);

        int totalWeight = 0;
        for (int weight : weights) totalWeight += weight;
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < compatible.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) return compatible.get(i);
        }
        return compatible.get(0);
    }

    private static int weightFor(TierEffect effect, int chanceIndex, int additionalChance) {
        int weight = effect.chanceAt(chanceIndex);
        if (weight <= LOW_WEIGHT_CAP) {
            weight = Math.min(weight + additionalChance, LOW_WEIGHT_CAP);
        }
        return weight;
    }

    private static TierEffect firstCompatible(List<TierEffect> effects, EquipmentSlot slot) {
        if (effects == null) return null;
        for (TierEffect effect : effects) {
            if (effect != null && effect.isCompatibleSlot(slot)) return effect;
        }
        return null;
    }

    private static EnumMap<Tier, List<TierEffect>> tiersFor(List<TierDropConfig.TierEquipmentConfig> entries) {
        List<TierDropConfig.TierEquipmentConfig> test = TierDropConfigLoader.equipment().test;
        List<TierDropConfig.TierEquipmentConfig> source = test.isEmpty() ? entries : test;
        return createTiersFromConfig(source);
    }

    private static EnumMap<Tier, List<TierEffect>> createTiersFromConfig(List<TierDropConfig.TierEquipmentConfig> list) {
        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (list == null) return tiers;

        for (TierDropConfig.TierEquipmentConfig entry : list) {
            if (entry == null || entry.prefix == null || entry.prefix.isBlank()) {
                TierDrop.LOGGER.warn("Skipping a tier entry that has no prefix");
                continue;
            }

            int[] chances = entry.chance == null ? new int[3] : Arrays.copyOf(entry.chance, Math.max(3, entry.chance.length));
            TierEffect effect = new TierEffect(entry.prefix, parseColor(entry.color), chances[0], chances[1], chances[2]);
            addConfiguredAttributes(effect, entry);
            if (entry.particles != null) {
                for (TierDropConfig.TierParticleConfig particle : entry.particles) {
                    effect.addParticle(particle);
                }
            }
            tiers.computeIfAbsent(tierOf(entry), ignored -> new ArrayList<>()).add(effect);
        }
        return tiers;
    }

    private static void addConfiguredAttributes(TierEffect effect, TierDropConfig.TierEquipmentConfig entry) {
        if (entry.attributes == null) return;

        for (Map<String, List<Object>> attribute : entry.attributes) {
            if (attribute == null) continue;
            for (Map.Entry<String, List<Object>> slotEntry : attribute.entrySet()) {
                List<Object> values = slotEntry.getValue();
                if (values == null || values.size() < 3) {
                    TierDrop.LOGGER.warn("Attribute '{}' on '{}' needs [name, min, max]", slotEntry.getKey(), entry.prefix);
                    continue;
                }

                Object minRaw = values.get(1);
                Object maxRaw = values.get(2);
                if (!(minRaw instanceof Number min) || !(maxRaw instanceof Number max)) {
                    TierDrop.LOGGER.warn("Attribute '{}' on '{}' has a non-numeric range", slotEntry.getKey(), entry.prefix);
                    continue;
                }

                Holder<Attribute> attributeType = attributeByName(String.valueOf(values.get(0)));
                if (attributeType == null) {
                    TierDrop.LOGGER.warn("Unknown attribute '{}' on tier '{}'", values.get(0), entry.prefix);
                    continue;
                }
                applyToSlots(effect, slotEntry.getKey(), attributeType, min.doubleValue(), max.doubleValue(), entry.prefix);
            }
        }
    }

    private static void applyToSlots(
            TierEffect effect, String slotName, Holder<Attribute> attribute,
            double min, double max, String prefix) {

        if (slotName != null && slotName.equalsIgnoreCase("all")) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                effect.addAttribute(slot, attribute, min, max);
            }
            return;
        }

        EquipmentSlot slot = toSlot(slotName);
        if (slot == null) {
            TierDrop.LOGGER.warn("Unknown equipment slot '{}' on tier '{}'", slotName, prefix);
            return;
        }
        effect.addAttribute(slot, attribute, min, max);
    }

    private static TextColor parseColor(String raw) {
        if (raw == null || raw.isBlank()) return TextColor.fromRgb(0xFFFFFF);
        try {
            var parsed = TextColor.parseColor(raw);
            if (parsed.isSuccess()) return parsed.getOrThrow();
        } catch (Exception ignored) {
            TierDrop.LOGGER.warn("Invalid tier color '{}'", raw);
        }
        return TextColor.fromRgb(0xFFFFFF);
    }

    /**
     * Uses the config {@code tier} field when it is set. Otherwise the display prefix is used,
     * which only matches when that prefix is itself a rarity name such as "Legendary".
     */
    private static Tier tierOf(TierDropConfig.TierEquipmentConfig entry) {
        String name = entry.tier != null && !entry.tier.isBlank() ? entry.tier : entry.prefix;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "common" -> Tier.COMMON;
            case "uncommon" -> Tier.UNCOMMON;
            case "rare" -> Tier.RARE;
            case "epic" -> Tier.EPIC;
            case "legendary" -> Tier.LEGENDARY;
            case "unique" -> Tier.UNIQUE;
            default -> Tier.UNIQUE;
        };
    }

    private static EquipmentSlot toSlot(String name) {
        if (name == null) return null;
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static Holder<Attribute> attributeByName(String name) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "ARMOR" -> Attributes.ARMOR;
            case "ARMOR_TOUGHNESS" -> Attributes.ARMOR_TOUGHNESS;
            case "ATTACK_DAMAGE" -> Attributes.ATTACK_DAMAGE;
            case "ATTACK_KNOCKBACK" -> Attributes.ATTACK_KNOCKBACK;
            case "ATTACK_SPEED" -> Attributes.ATTACK_SPEED;
            case "BLOCK_BREAK_SPEED" -> Attributes.BLOCK_BREAK_SPEED;
            case "BLOCK_INTERACTION_RANGE" -> Attributes.BLOCK_INTERACTION_RANGE;
            case "BURNING_TIME" -> Attributes.BURNING_TIME;
            case "EXPLOSION_KNOCKBACK_RESISTANCE" -> Attributes.EXPLOSION_KNOCKBACK_RESISTANCE;
            case "ENTITY_INTERACTION_RANGE" -> Attributes.ENTITY_INTERACTION_RANGE;
            case "FALL_DAMAGE_MULTIPLIER" -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case "FLYING_SPEED" -> Attributes.FLYING_SPEED;
            case "FOLLOW_RANGE" -> Attributes.FOLLOW_RANGE;
            case "GRAVITY" -> Attributes.GRAVITY;
            case "JUMP_STRENGTH" -> Attributes.JUMP_STRENGTH;
            case "KNOCKBACK_RESISTANCE" -> Attributes.KNOCKBACK_RESISTANCE;
            case "LUCK" -> Attributes.LUCK;
            case "MAX_ABSORPTION" -> Attributes.MAX_ABSORPTION;
            case "MAX_HEALTH" -> Attributes.MAX_HEALTH;
            case "MINING_EFFICIENCY" -> Attributes.MINING_EFFICIENCY;
            case "MOVEMENT_EFFICIENCY" -> Attributes.MOVEMENT_EFFICIENCY;
            case "MOVEMENT_SPEED" -> Attributes.MOVEMENT_SPEED;
            case "OXYGEN_BONUS" -> Attributes.OXYGEN_BONUS;
            case "SAFE_FALL_DISTANCE" -> Attributes.SAFE_FALL_DISTANCE;
            case "SCALE" -> Attributes.SCALE;
            case "SNEAKING_SPEED" -> Attributes.SNEAKING_SPEED;
            case "STEP_HEIGHT" -> Attributes.STEP_HEIGHT;
            case "SUBMERGED_MINING_SPEED" -> Attributes.SUBMERGED_MINING_SPEED;
            case "SWEEPING_DAMAGE_RATIO" -> Attributes.SWEEPING_DAMAGE_RATIO;
            case "WATER_MOVEMENT_EFFICIENCY" -> Attributes.WATER_MOVEMENT_EFFICIENCY;
            case "CAMERA_DISTANCE" -> Attributes.CAMERA_DISTANCE;
            case "SPAWN_REINFORCEMENTS_CHANCE" -> Attributes.SPAWN_REINFORCEMENTS_CHANCE;
            case "TEMPT_RANGE" -> Attributes.TEMPT_RANGE;
            case "WAYPOINT_TRANSMIT_RANGE" -> Attributes.WAYPOINT_TRANSMIT_RANGE;
            case "WAYPOINT_RECEIVE_RANGE" -> Attributes.WAYPOINT_RECEIVE_RANGE;
            default -> null;
        };
    }
}
