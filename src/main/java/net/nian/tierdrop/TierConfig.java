package net.nian.tierdrop;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

import java.util.*;

/**
 * Central tier/effect configuration loaded from config and hardcoded special tiers.
 *
 * 26.1 mapping changes from 1.21.1 Yarn:
 *  - net.minecraft.entity.attribute.EntityAttributes  → net.minecraft.world.entity.ai.attributes.Attributes
 *  - GENERIC_ARMOR, GENERIC_MAX_HEALTH etc.           → Attributes.ARMOR, Attributes.MAX_HEALTH etc.
 *  - net.minecraft.entity.EquipmentSlot               → net.minecraft.world.entity.EquipmentSlot
 *  - net.minecraft.registry.entry.RegistryEntry       → net.minecraft.core.Holder
 *  - net.minecraft.util.Formatting                    → net.minecraft.ChatFormatting
 *  - net.minecraft.util.math.random.Random            → net.minecraft.util.RandomSource
 *  - StatusEffects / StatusEffect                     → MobEffects / MobEffect
 *  - StatusEffectInstance                             → MobEffectInstance
 *  - Registries.STATUS_EFFECT                         → BuiltInRegistries.MOB_EFFECT
 */
public class TierConfig {

    public enum ItemCategory {
        ARMOR, WEAPON, MININGTOOL, RANGE_WEAPON
    }

    public enum Tier {
        COMMON, UNCOMMON, RARE, EPIC, UNIQUE, LEGENDARY, MAGIC
    }

    // -------------------------------------------------------------------------
    // TierEffect
    // -------------------------------------------------------------------------

    public static class TierEffect {
        public final String prefixName;
        public final TextColor color;
        public final int[] chances;

        private final Map<EquipmentSlot, Map<Holder<Attribute>, MinMaxDouble>> slotAttributes =
                new EnumMap<>(EquipmentSlot.class);

        private final List<MobEffectInstance> statusEffects = new ArrayList<>();
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
            if (attribute == null) return;
            slotAttributes.computeIfAbsent(slot, s -> new HashMap<>()).put(attribute, new MinMaxDouble(min, max));
        }

        public void addStatusEffect(Holder<MobEffect> effect, int level) {
            statusEffects.add(new MobEffectInstance(effect, Integer.MAX_VALUE, level - 1, true, false));
        }

        public void addStatusEffectTrigger(String value)      { statusEffectTrigger = value; }
        public void addStatusEffectTarget(String value)       { statusEffectTarget = value; }
        public void addStatusEffectTriggerChance(int value)   { statusEffectTriggerChance = value; }
        public void addStatusEffectDuration(int value)        { statusEffectDuration = value; }
        public void addStatusEffectName(String value)         { statusEffectName = value; }
        public void addStatusEffectDescription(String value)  { statusEffectDescription = value; }

        public Map<Holder<Attribute>, MinMaxDouble> getAttributesForSlot(EquipmentSlot slot) {
            return slotAttributes.getOrDefault(slot, Collections.emptyMap());
        }

        public List<MobEffectInstance> getStatusEffects()    { return statusEffects; }
        public String getStatusEffectName()                  { return statusEffectName; }
        public String getStatusEffectTrigger()               { return statusEffectTrigger; }
        public String getStatusEffectTarget()                { return statusEffectTarget; }
        public int getStatusEffectTriggerChance()            { return statusEffectTriggerChance; }
        public int getStatusEffectDuration()                 { return statusEffectDuration; }
        public String getStatusEffectDescription()           { return statusEffectDescription; }

        public Map<EquipmentSlot, Map<Holder<Attribute>, MinMaxDouble>> getAllSlotAttributes() {
            return slotAttributes;
        }

        public Component getFormattedPrefix() {
            return Component.literal(prefixName + " ")
                    .withStyle(Style.EMPTY
                            .withColor(color)
                            .withItalic(false));
        }


        public boolean isCompatibleSlot(EquipmentSlot slot) {
            Map<Holder<Attribute>, MinMaxDouble> attrs = slotAttributes.get(slot);
            return attrs != null && !attrs.isEmpty();
        }

        private final List<TierDropConfig.TierParticleConfig> particleConfigs = new ArrayList<>();

        public void addParticle(TierDropConfig.TierParticleConfig config) {
            if (config != null) {
                this.particleConfigs.add(config);
            }
        }

        public List<TierDropConfig.TierParticleConfig> getParticles() {
            return particleConfigs;
        }
    }

    // -------------------------------------------------------------------------
    // Config map
    // -------------------------------------------------------------------------

    public static final EnumMap<ItemCategory, EnumMap<Tier, List<TierEffect>>> CONFIG =
            new EnumMap<>(ItemCategory.class);

    static {
        CONFIG.put(ItemCategory.ARMOR, createArmorTiers());
        CONFIG.put(ItemCategory.WEAPON, createWeaponTiers());
        CONFIG.put(ItemCategory.MININGTOOL, createToolTiers());
        CONFIG.put(ItemCategory.RANGE_WEAPON, createRangeWeaponTiers());
    }

    // -------------------------------------------------------------------------
    // Public helpers
    // -------------------------------------------------------------------------

    public static boolean hasTier(ItemStack stack, String tierName) {
        return stack.getHoverName().getString().contains(tierName);
    }

    public static TierEffect getRandomTierEffect(
            RandomSource random, ItemCategory category, int appliedLevel,
            EquipmentSlot slot, int additionalChance) {

        if (category == null) return null;

        Map<Tier, List<TierEffect>> effectsByTier = CONFIG.get(category);
        int clampedLevel = appliedLevel - 1;

        List<TierEffect> compatibleEffects = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();

        for (Tier tier : Tier.values()) {
            List<TierEffect> effects = effectsByTier.get(tier);
            if (effects == null) continue;
            for (TierEffect effect : effects) {
                if (effect == null || !effect.isCompatibleSlot(slot)) continue;
                int weight = effect.chances[clampedLevel];
                if (weight <= 20) {
                    weight = Math.min(weight + additionalChance, 20);
                }
                if (weight > 0) {
                    compatibleEffects.add(effect);
                    weights.add(weight);
                }
            }
        }

        if (compatibleEffects.isEmpty()) {
            List<TierEffect> fallback = effectsByTier.get(Tier.COMMON);
            if (fallback != null) {
                for (TierEffect effect : fallback) {
                    if (effect != null && effect.isCompatibleSlot(slot)) return effect;
                }
            }
            return null;
        }

        int totalWeight = weights.stream().mapToInt(i -> i).sum();
        if (totalWeight <= 0) return null;

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < compatibleEffects.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) return compatibleEffects.get(i);
        }

        return compatibleEffects.get(0);
    }

    public static Tier getTierBySlotAttributes(
            ItemCategory category, EquipmentSlot slot, TierEffect targetEffect) {

        if (category == null || targetEffect == null) return null;
        EnumMap<Tier, List<TierEffect>> effectsByTier = CONFIG.get(category);

        for (Map.Entry<Tier, List<TierEffect>> entry : effectsByTier.entrySet()) {
            for (TierEffect effect : entry.getValue()) {
                if (effect == targetEffect) return entry.getKey();
                if (effect.prefixName.equals(targetEffect.prefixName)
                        && effect.color == targetEffect.color
                        && effect.getAttributesForSlot(slot).equals(targetEffect.getAttributesForSlot(slot))) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Config-driven tier builders
    // -------------------------------------------------------------------------

    private static EnumMap<Tier, List<TierEffect>> createArmorTiers() {
        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (!TierDropConfigLoader.config.TierEquipment.Test.isEmpty()) {
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Test));
        } else {
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Armor));
//            tiers.put(Tier.MAGIC, List.of(
//                    resilientTier(), heroOfTheVillage(),
//                    moltenAegisArmorTier(), natureEmbraceTier(), everbloomTier()
//            ));
        }
        return tiers;
    }

    private static EnumMap<Tier, List<TierEffect>> createWeaponTiers() {
        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (!TierDropConfigLoader.config.TierEquipment.Test.isEmpty())
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Test));
        else
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Weapon));
        return tiers;
    }

    private static EnumMap<Tier, List<TierEffect>> createRangeWeaponTiers() {
        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (!TierDropConfigLoader.config.TierEquipment.Test.isEmpty())
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Test));
        else
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.RangeWeapon));
        return tiers;
    }

    private static EnumMap<Tier, List<TierEffect>> createToolTiers() {
        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (!TierDropConfigLoader.config.TierEquipment.Test.isEmpty())
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.Test));
        else
            tiers.putAll(createTiersFromConfig(TierDropConfigLoader.config.TierEquipment.MiningTool));
        return tiers;
    }

    public static EnumMap<Tier, List<TierEffect>> createTiersFromConfig(
            List<TierDropConfig.TierEquipmentConfig> list) {

        EnumMap<Tier, List<TierEffect>> tiers = new EnumMap<>(Tier.class);
        if (list == null) return tiers;

        for (TierDropConfig.TierEquipmentConfig ct : list) {
            Tier tierEnum = getTier(ct.prefix);
            TextColor tierColor = TextColor.fromRgb(0xFFFFFF);
            try { // This parses "#RRGGBB" or Minecraft color names like "red"
                var parsed = TextColor.parseColor(ct.color);
                if (parsed.isSuccess()) {
                    tierColor = parsed.getOrThrow();
                } } catch (Exception ignored) {}

            TierEffect effect = new TierEffect(ct.prefix, tierColor,
                    ct.chance[0], ct.chance[1], ct.chance[2]);

            for (Map<String, List<Number>> attribute : ct.attributes) {
                for (Map.Entry<String, List<Number>> entry : attribute.entrySet()) {
                    EquipmentSlot slot = toSlot(entry.getKey());
                    List<Number> values = entry.getValue();
                    Holder<Attribute> attr = getAttributeByName(values.get(0).toString());
                    double attrMin = values.get(1).doubleValue();
                    double attrMax = values.get(2).doubleValue();

                    if (slot == null) {
                        for (EquipmentSlot s : EquipmentSlot.values()) {
                            effect.addAttribute(s, attr, attrMin, attrMax);
                        }
                    } else {
                        effect.addAttribute(slot, attr, attrMin, attrMax);
                    }
                }
            }

            //particles
            if (ct.particles != null) {
                for (TierDropConfig.TierParticleConfig p : ct.particles) {
                    effect.addParticle(p);
                }
            }

            tiers.computeIfAbsent(tierEnum, k -> new ArrayList<>()).add(effect);
        }
        return tiers;
    }

    // -------------------------------------------------------------------------
    // Attribute name → Holder lookup  (26.1 Mojang mapping names)
    // -------------------------------------------------------------------------

    public static Holder<Attribute> getAttributeByName(String name) {
        return switch (name.toUpperCase()) {
            case "ARMOR"                        -> Attributes.ARMOR;
            case "ARMOR_TOUGHNESS"              -> Attributes.ARMOR_TOUGHNESS;
            case "ATTACK_DAMAGE"                -> Attributes.ATTACK_DAMAGE;
            case "ATTACK_KNOCKBACK"             -> Attributes.ATTACK_KNOCKBACK;
            case "ATTACK_SPEED"                 -> Attributes.ATTACK_SPEED;
            case "BLOCK_BREAK_SPEED"            -> Attributes.BLOCK_BREAK_SPEED;
            case "BLOCK_INTERACTION_RANGE"      -> Attributes.BLOCK_INTERACTION_RANGE;
            case "BURNING_TIME"                 -> Attributes.BURNING_TIME;
            case "EXPLOSION_KNOCKBACK_RESISTANCE" -> Attributes.EXPLOSION_KNOCKBACK_RESISTANCE;
            case "ENTITY_INTERACTION_RANGE"     -> Attributes.ENTITY_INTERACTION_RANGE;
            case "FALL_DAMAGE_MULTIPLIER"       -> Attributes.FALL_DAMAGE_MULTIPLIER;
            case "FLYING_SPEED"                 -> Attributes.FLYING_SPEED;
            case "FOLLOW_RANGE"                 -> Attributes.FOLLOW_RANGE;
            case "GRAVITY"                      -> Attributes.GRAVITY;
            case "JUMP_STRENGTH"                -> Attributes.JUMP_STRENGTH;
            case "KNOCKBACK_RESISTANCE"         -> Attributes.KNOCKBACK_RESISTANCE;
            case "LUCK"                         -> Attributes.LUCK;
            case "MAX_ABSORPTION"               -> Attributes.MAX_ABSORPTION;
            case "MAX_HEALTH"                   -> Attributes.MAX_HEALTH;
            case "MINING_EFFICIENCY"            -> Attributes.MINING_EFFICIENCY;
            case "MOVEMENT_EFFICIENCY"          -> Attributes.MOVEMENT_EFFICIENCY;
            case "MOVEMENT_SPEED"               -> Attributes.MOVEMENT_SPEED;
            case "OXYGEN_BONUS"                 -> Attributes.OXYGEN_BONUS;
            case "SAFE_FALL_DISTANCE"           -> Attributes.SAFE_FALL_DISTANCE;
            case "SCALE"                        -> Attributes.SCALE;
            case "SNEAKING_SPEED"               -> Attributes.SNEAKING_SPEED;
            case "STEP_HEIGHT"                  -> Attributes.STEP_HEIGHT;
            case "SUBMERGED_MINING_SPEED"       -> Attributes.SUBMERGED_MINING_SPEED;
            case "SWEEPING_DAMAGE_RATIO"        -> Attributes.SWEEPING_DAMAGE_RATIO;
            case "WATER_MOVEMENT_EFFICIENCY"    -> Attributes.WATER_MOVEMENT_EFFICIENCY;
            case "CAMERA_DISTANCE"              -> Attributes.CAMERA_DISTANCE;
            case "SPAWN_REINFORCEMENTS_CHANCE"  -> Attributes.SPAWN_REINFORCEMENTS_CHANCE;
            case "TEMPT_RANGE"                  -> Attributes.TEMPT_RANGE;
            case "WAYPOINT_TRANSMIT_RANGE"      -> Attributes.WAYPOINT_TRANSMIT_RANGE;
            case "WAYPOINT_RECEIVE_RANGE"       -> Attributes.WAYPOINT_RECEIVE_RANGE;
            default -> null;
        };
    }

    public static Tier getTier(String name) {
        return switch (name.toLowerCase()) {
            case "common"   -> Tier.COMMON;
            case "uncommon" -> Tier.UNCOMMON;
            case "rare"     -> Tier.RARE;
            case "epic"     -> Tier.EPIC;
            case "legendary"-> Tier.LEGENDARY;
            case "unique"   -> Tier.UNIQUE;
            default         -> Tier.UNIQUE;
        };
    }

    public static EquipmentSlot toSlot(String name) {
        return switch (name.toLowerCase()) {
            case "head"     -> EquipmentSlot.HEAD;
            case "chest"    -> EquipmentSlot.CHEST;
            case "legs"     -> EquipmentSlot.LEGS;
            case "feet"     -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand"  -> EquipmentSlot.OFFHAND;
            case "all"      -> null;
            default         -> EquipmentSlot.OFFHAND;
        };
    }

    // -------------------------------------------------------------------------
    // Hardcoded special tiers (armors with status effects)
    // -------------------------------------------------------------------------

//    private static TierEffect resilientTier() {
//        TierEffect effect = new TierEffect("Resilient", ChatFormatting.DARK_GREEN, 0, 8, 8);
//        effect.addAttribute(EquipmentSlot.CHEST, Attributes.BURNING_TIME, -0.3, -0.1);
//        effect.addAttribute(EquipmentSlot.CHEST, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 0.1, 0.2);
//        effect.addStatusEffect(MobEffects.REGENERATION, 1);
//        effect.addStatusEffectName("REGENERATION");
//        effect.addStatusEffectTrigger("lowhealth");
//        effect.addStatusEffectTriggerChance(50);
//        effect.addStatusEffectTarget("player");
//        effect.addStatusEffectDuration(3);
//        effect.addStatusEffectDescription("Regeneration chance");
//        return effect;
//    }
//
//    private static TierEffect heroOfTheVillage() {
//        TierEffect effect = new TierEffect("Champion's", ChatFormatting.GREEN, 0, 5, 8);
//        effect.addAttribute(EquipmentSlot.HEAD, Attributes.ARMOR, 4, 5);
//        effect.addAttribute(EquipmentSlot.HEAD, Attributes.MAX_HEALTH, 18, 32);
//        effect.addStatusEffect(MobEffects.HERO_OF_THE_VILLAGE, 1);
//        effect.addStatusEffectName("HERO_OF_THE_VILLAGE");
//        effect.addStatusEffectTrigger("fullhealth");
//        effect.addStatusEffectTriggerChance(25);
//        effect.addStatusEffectTarget("player");
//        effect.addStatusEffectDuration(25);
//        effect.addStatusEffectDescription("Hero of the Village");
//        return effect;
//    }
//
//    private static TierEffect moltenAegisArmorTier() {
//        TierEffect effect = new TierEffect("Molten Aegis", ChatFormatting.DARK_RED, 0, 5, 8);
//        for (EquipmentSlot slot : EquipmentSlot.values()) {
//            effect.addAttribute(slot, Attributes.ARMOR, 2, 4);
//            effect.addAttribute(slot, Attributes.MAX_HEALTH, 10, 12);
//        }
//        effect.addStatusEffect(MobEffects.FIRE_RESISTANCE, 1);
//        effect.addStatusEffectName("FIRE_RESISTANCE");
//        effect.addStatusEffectTrigger("lowhealth");
//        effect.addStatusEffectTriggerChance(75);
//        effect.addStatusEffectTarget("player");
//        effect.addStatusEffectDuration(6);
//        effect.addStatusEffectDescription("Fire Resistance chance");
//        return effect;
//    }
//
//    private static TierEffect natureEmbraceTier() {
//        TierEffect effect = new TierEffect("Nature's Embrace", ChatFormatting.DARK_GREEN, 0, 8, 8);
//        for (EquipmentSlot slot : EquipmentSlot.values()) {
//            effect.addAttribute(slot, Attributes.ARMOR, 1, 2);
//            effect.addAttribute(slot, Attributes.MAX_HEALTH, 4, 12);
//        }
//        effect.addStatusEffect(MobEffects.REGENERATION, 1);
//        effect.addStatusEffectName("REGENERATION");
//        effect.addStatusEffectTrigger("lowhealth");
//        effect.addStatusEffectTriggerChance(50);
//        effect.addStatusEffectTarget("player");
//        effect.addStatusEffectDuration(4);
//        effect.addStatusEffectDescription("Regeneration chance");
//        return effect;
//    }
//
//    private static TierEffect everbloomTier() {
//        TierEffect effect = new TierEffect("Everbloom", ChatFormatting.DARK_GREEN, 0, 8, 8);
//        for (EquipmentSlot slot : EquipmentSlot.values()) {
//            effect.addAttribute(slot, Attributes.ARMOR, 1, 2);
//            effect.addAttribute(slot, Attributes.MAX_HEALTH, 4, 12);
//        }
//        effect.addStatusEffect(MobEffects.REGENERATION, 1);
//        effect.addStatusEffectName("REGENERATION");
//        effect.addStatusEffectTrigger("lowhealth");
//        effect.addStatusEffectTriggerChance(50);
//        effect.addStatusEffectTarget("player");
//        effect.addStatusEffectDuration(4);
//        effect.addStatusEffectDescription("Regeneration chance");
//        return effect;
//    }
}
