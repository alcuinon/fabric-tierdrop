package net.nian.tierdrop;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Core handler for applying tier effects to ItemStacks.
 *
 * 26.1 changes from 1.21.1:
 *  - SwordItem / ArmorItem / MiningToolItem / RangedWeaponItem are removed.
 *    Detection is now done via DataComponents: WEAPON, ARMOR, TOOL.
 *  - AttributeModifiersComponent  → ItemAttributeModifiers
 *  - DataComponentTypes           → DataComponents
 *  - NbtComponent                 → CustomData
 *  - LoreComponent                → ItemLore
 *  - Identifier.of()              → ResourceLocation.fromNamespaceAndPath()
 *  - RegistryEntry<EntityAttribute> → Holder<Attribute>
 *  - Text / Style / Formatting    → Component / Style / ChatFormatting
 *  - ServerPlayerEntity           → ServerPlayer
 *  - Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME) → Stats.CUSTOM_TIME_SINCE_REST (nearest equivalent)
 */
public class ItemTierHandler {

    // -------------------------------------------------------------------------
    // Item classification (replaces SwordItem/ArmorItem/MiningToolItem checks)
    // -------------------------------------------------------------------------

    public static boolean isWeapon(ItemStack stack) {
        return stack.has(DataComponents.WEAPON);
    }
    //.ARMOR is not exist
    public static boolean isArmor(ItemStack stack) { return stack.has(DataComponents.EQUIPPABLE); }

    public static boolean isTool(ItemStack stack) {
        return stack.has(DataComponents.TOOL);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof net.minecraft.world.item.ProjectileWeaponItem  // Bow, Crossbow
                || item instanceof net.minecraft.world.item.TridentItem;
    }
    public static boolean isForPlayer(ItemStack stack) {
        // If it's not equippable, we assume it's a tool/weapon held in hand
        if (!stack.has(DataComponents.EQUIPPABLE)) {
            return true;
        }

        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return false;

        // Verify the slot type is Humanoid (Head, Chest, Legs, Feet)
        // Non-player items like Wolf Armor use different internal slot logic
        // or aren't categorized as HUMANOID_ARMOR.
        return equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }

    public static boolean isWeaponOrArmorOrTool(ItemStack stack) {
        if (!isForPlayer(stack)) {
            return false;
        }

        return isWeapon(stack) || isArmor(stack) || isTool(stack) || isRangedWeapon(stack);
    }

    // -------------------------------------------------------------------------
    // Tier presence check
    // -------------------------------------------------------------------------

    public static boolean shouldApplyTier(ItemStack stack) {
        return isWeaponOrArmorOrTool(stack) && !hasTierAlready(stack);
    }

    public static boolean hasTierAlready(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        Optional<CompoundTag> inner = data.copyTag().getCompound("tierdrop:origin");
        return !inner.get().getString("tierDrop").isEmpty(); //check here
    }

    // -------------------------------------------------------------------------
    // Vault proximity helpers
    // -------------------------------------------------------------------------

    public static boolean isNearTrialVault(ServerPlayer player) {
        return isNearVault(player, false);
    }

    public static boolean isNearOminousTrialVault(ServerPlayer player) {
        return isNearVault(player, true);
    }

    private static boolean isNearVault(ServerPlayer player, boolean requireOminous) {
        int radius = 6;
        BlockPos center = player.blockPosition();
        Level world = player.level();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof VaultBlockEntity vault) {
                if (!requireOminous) return true;
                BlockState state = vault.getBlockState();
                if (state.hasProperty(VaultBlock.OMINOUS) && state.getValue(VaultBlock.OMINOUS)) {
                    return true;
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Jump-strength tier check (used for fall-damage cancellation)
    // -------------------------------------------------------------------------
    //unused for now
    public static boolean hasJumpStrengthTier(LivingEntity entity) {
        ItemStack legs = entity.getItemBySlot(EquipmentSlot.LEGS);
        return TierConfig.hasTier(legs, "Skybound") || TierConfig.hasTier(legs, "Gale");
    }

    // -------------------------------------------------------------------------
    // Main application entry point
    // -------------------------------------------------------------------------

    public static void applyTierIfEligible(
            ItemStack stack, ServerPlayer player, int appliedLevel,
            int addChance, int minTierLevel) {

        // 1. Safety Check: If the item shouldn't have a tier, exit early to save CPU
        if (!shouldApplyTier(stack)) return;

        // 2. Resolve Category and Slot early
        TierConfig.ItemCategory category = getItemCategory(stack);
        EquipmentSlot slot = getRelevantSlot(stack);

        // 3. Null Safety: If category is null (e.g. someone tries to tier a Carrot), stop.
        if (category == null || slot == null) return;

        // --- SLOWER PLAYTIME PROGRESSION ---
        // Playtime-based bonus chance (every 20 in-game days ≈ extra 1)

        int ticksLived = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));

        int rawAdditionalChance = (ticksLived / (20 * 60 * 20) / 2) + addChance;

        // --- THE HARD CAP ---
        int additionalChance = Math.min(rawAdditionalChance, 10);

        TierConfig.TierEffect effect = TierConfig.getRandomTierEffect(
                player.getRandom(), // Standard random is sufficient
                category,
                appliedLevel,
                slot,
                additionalChance
        );

        if (effect != null) {
            int finalTier = applyAttributesToItem(stack, effect, category, minTierLevel);

            // --- Dynamic Sound Feedback ---
            float pitch = 0.8f + (finalTier * 0.12f); // Pitch goes from 0.9 to 2.0

            if (finalTier >= 9) {
                // Legendary/Cursed Tier: Play an explosion-like sparkle or Totem sound
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.5f);
            } else if (finalTier >= 7) {
                // High Tier: Powerful vault sound
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, pitch);
            } else {
                // Low/Mid Tier: Standard click/pop
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, pitch);
            }
        }
    }

    public static void processMobTiers(Mob mob, ServerLevel serverLevel, int ELITE_SPAWN_CHANCE, int BASE_CHANCE_PERCENT, int BOSS_SPAWN_CHANCE) {
        String mobName = mob.getType().toShortString();

        if(mob.getType().getCategory() != MobCategory.MONSTER) return;

        // We don't have spawnReason here so use difficulty to scale
        // and treat everything as NATURAL baseline
        // If you need spawnReason you can store it via TierDropSpawnContext
        // from a separate lightweight Mob.finalizeSpawn inject
        int addChance    = 0;
        int minTierLevel = -5;
        int appliedLevel = 1;

        float diffScale   = serverLevel.getCurrentDifficultyAt(mob.blockPosition()).getSpecialMultiplier();
        int   diffBonus   = (int)(diffScale * 10);
        int   finalChance = addChance + diffBonus;

        boolean isElite = new Random().nextInt(ELITE_SPAWN_CHANCE) <= 5;
        if (isElite) {
            appliedLevel = 3;
            minTierLevel = 2;
            finalChance  = 25 + diffBonus;
//            TierDrop.LOGGER.info("[TierDrop|Mob] ★ ELITE: {}", mobName);
        }

        boolean isBoss = new Random().nextInt(BOSS_SPAWN_CHANCE) <= 1;
        if (isBoss) {
            appliedLevel = 3;
            minTierLevel = 5;
            finalChance  = 25 + diffBonus;
        }

        Random  rng       = new Random();
        boolean tieredAny = false;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (!ItemTierHandler.isWeaponOrArmorOrTool(stack)) continue;
            if (ItemTierHandler.hasTierAlready(stack)) continue;

            if (!isElite && !isBoss) {
                int rolled = rng.nextInt(150);
                int needed = BASE_CHANCE_PERCENT + finalChance;
                if (rolled >= needed) {
//                    TierDrop.LOGGER.info("[TierDrop|Mob]   {} failed roll ({}/{})",
//                            stack.getItem().getDescriptionId(), rolled, needed);
                    continue;
                }
            }

            EquipmentSlot relevantSlot  = ItemTierHandler.getRelevantSlot(stack);
            TierConfig.ItemCategory cat = ItemTierHandler.getItemCategory(stack);
            if (relevantSlot == null || cat == null) continue;

            TierConfig.TierEffect effect = TierConfig.getRandomTierEffect(
                    serverLevel.getRandom(), cat, appliedLevel, relevantSlot, finalChance);

            if (effect == null) {
//                TierDrop.LOGGER.warn("[TierDrop|Mob]   No effect for {} cat={} slot={}",
//                        stack.getItem().getDescriptionId(), cat, relevantSlot.name());
                continue;
            }

            applyAttributesToItem(stack, effect, cat, minTierLevel);
            mob.setItemSlot(slot, stack);
            tieredAny = true;
//            TierDrop.LOGGER.info("[TierDrop|Mob]   ✔ Tiered {} with '{}' on {}",
//                    stack.getItem().getDescriptionId(), effect.prefixName, mobName);
        }

        if (isElite && tieredAny) {
            mob.setCustomName(
                    Component.empty()
                            .append(Component.literal("★ Elite ")
                                    .withStyle(s -> s.withColor(0xFF4444).withBold(true)))
                            .append(mob.getType().getDescription()
                                    .copy()
                                    .withStyle(s -> s.withColor(0xFF9999).withBold(false)))
            );
            //mob.setCustomNameVisible(true);
            var maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);

            if (maxHealthAttr != null) {
                // 2. Increase the maximum capacity so it can hold the extra 200 HP
                maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() + 50);

                // 3. Now set the current health to the new maximum
                mob.setHealth((float) maxHealthAttr.getValue());
            }
            // Loot Table Buffs (25% drop chance for all gear)
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.15f); // Note: 26.1.1 often uses 0.0-1.0f for float chances
            }
        }
        else if(isBoss && tieredAny){
            // Colors defined as Hex for clarity
            int crownColor = 0xFFD700; // Gold
            int bossLabelColor = 0xFF2222; // Deeper Red for "Boss"
            int mobNameColor = 0xFFAAAA; // Lighter Rose for the entity name

            mob.setCustomName(
                    Component.empty()
                            .append(Component.literal("👑 ") // The Crown Icon
                                    .withStyle(s -> s.withColor(crownColor)))
                            .append(Component.literal("Boss ")
                                    .withStyle(s -> s.withColor(bossLabelColor).withBold(true)))
                            .append(mob.getType().getDescription()
                                    .copy()
                                    .withStyle(s -> s.withColor(mobNameColor).withBold(false)))
            );

            //mob.setCustomNameVisible(true);

            // Buffing the Boss
            var maxHealthAttr = mob.getAttribute(Attributes.MAX_HEALTH);

            if (maxHealthAttr != null) {
                // 2. Increase the maximum capacity so it can hold the extra 200 HP
                maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() + 200);

                // 3. Now set the current health to the new maximum
                mob.setHealth((float) maxHealthAttr.getValue());
            }

            // Loot Table Buffs (25% drop chance for all gear)
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                mob.setDropChance(slot, 0.35f); // Note: 26.1.1 often uses 0.0-1.0f for float chances
            }
        }

        if (!tieredAny) {
//            TierDrop.LOGGER.info("[TierDrop|Mob] {} — no slots tiered", mobName);
        }
    }

    // -------------------------------------------------------------------------
    // Category / slot resolution
    // -------------------------------------------------------------------------

    public static TierConfig.ItemCategory getItemCategory(ItemStack stack) {
        // 1. Armor and Ranged are distinct, so we check them first
        if (isArmor(stack))        return TierConfig.ItemCategory.ARMOR;
        if (isRangedWeapon(stack)) return TierConfig.ItemCategory.RANGE_WEAPON;

        // 2. Use Item Tags to catch Tools specifically.
        // This ensures Pickaxes, Axes, Shovels, and Hoes are always MININGTOOL
        if (stack.is(ItemTags.PICKAXES) ||
                stack.is(ItemTags.AXES) ||
                stack.is(ItemTags.SHOVELS) ||
                stack.is(ItemTags.HOES)) {
            return TierConfig.ItemCategory.MININGTOOL;
        }

        // 3. If it wasn't a specific tool but has the WEAPON component, it's a Melee Weapon.
        // This catches Swords and Maces.
        if (isWeapon(stack)) {
            return TierConfig.ItemCategory.WEAPON;
        }

        // 4. Fallback for any other tools that might not have tags (Shears, Brush, etc.)
        if (isTool(stack)) {
            return TierConfig.ItemCategory.MININGTOOL;
        }

        return null;
    }

    /**
     * Determines the relevant EquipmentSlot for an item.
     * For armor we read the EQUIPPABLE component (26.1); for weapons/tools it's MAINHAND.
     */
    public static EquipmentSlot getRelevantSlot(ItemStack stack) {
        // Armor: read from the EQUIPPABLE data component
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            return equippable.slot();
        }
        // Weapons, tools, ranged weapons all go to MAINHAND
        if (isWeapon(stack) || isTool(stack) || isRangedWeapon(stack)) {
            return EquipmentSlot.MAINHAND;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // UUID per slot (stable modifier IDs)
    // -------------------------------------------------------------------------

    private static final Map<EquipmentSlot, UUID> SLOT_UUIDS = Map.of(
            EquipmentSlot.HEAD,     UUID.fromString("11111111-1111-1111-1111-111111111111"),
            EquipmentSlot.CHEST,    UUID.fromString("22222222-2222-2222-2222-222222222222"),
            EquipmentSlot.LEGS,     UUID.fromString("33333333-3333-3333-3333-333333333333"),
            EquipmentSlot.FEET,     UUID.fromString("44444444-4444-4444-4444-444444444444"),
            EquipmentSlot.MAINHAND, UUID.fromString("55555555-5555-5555-5555-555555555555"),
            EquipmentSlot.OFFHAND,  UUID.fromString("66666666-6666-6666-6666-666666666666")
    );

    // -------------------------------------------------------------------------
    // Attribute application
    // -------------------------------------------------------------------------

    public static int applyAttributesToItem(
            ItemStack itemStack, TierConfig.TierEffect effect,
            TierConfig.ItemCategory category, int minTierLevel) {

        EquipmentSlot slot = getRelevantSlot(itemStack);
        if (slot == null) return 0;

        int tierLevel = new Random().nextInt(10) + 1 + minTierLevel;
        tierLevel = Math.max(1, Math.min(10, tierLevel));

        Map<Holder<Attribute>, MinMaxDouble> attributes = effect.getAttributesForSlot(slot);
        Map<Holder<Attribute>, Double> effectFinal = addAttributeModifiers(attributes, itemStack, slot, tierLevel);

        addTierPrefix(itemStack, effect, tierLevel);
        addCustomLore(itemStack, effect, category, effectFinal);
        storeEffectTag(itemStack, effect);

        return tierLevel;
    }

    private static Map<Holder<Attribute>, Double> addAttributeModifiers(
            Map<Holder<Attribute>, MinMaxDouble> attributes,
            ItemStack itemStack, EquipmentSlot slot, int tierLevel) {

        Map<Holder<Attribute>, Double> effectFinal = new HashMap<>();

        // 1. Read existing item attribute modifiers
        ItemAttributeModifiers base = itemStack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        // 2. Build a new list starting from existing entries
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }

        // 3. Add our custom tier modifiers
        for (Map.Entry<Holder<Attribute>, MinMaxDouble> entry : attributes.entrySet()) {
            double amount = entry.getValue().getRandom(new Random(), tierLevel);
            UUID uuid = SLOT_UUIDS.get(slot);
            if (uuid == null) continue;

            Identifier id = Identifier.fromNamespaceAndPath(TierDrop.MOD_ID, uuid.toString().replace("-", "_"));

            AttributeModifier modifier = new AttributeModifier(
                    id, amount, AttributeModifier.Operation.ADD_VALUE);

            builder.add(entry.getKey(), modifier, EquipmentSlotGroup.bySlot(slot));
            effectFinal.put(entry.getKey(), amount);
        }

        itemStack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return effectFinal;
    }

    // -------------------------------------------------------------------------
    // Name / lore helpers
    // -------------------------------------------------------------------------

    private static void addTierPrefix(ItemStack itemStack, TierConfig.TierEffect effect, int tierLevel) {
        String roman = toRoman(tierLevel);
        String itemName = itemStack.getItem().getDefaultInstance().getHoverName().getString().toLowerCase();

        // Clean material names from the item name to make the prefix feel more natural
        String[] materials = {
                "iron", "diamond", "netherite", "golden", "leather", "chainmail",
                "wooden", "stone", "copper", "emerald", "lapis", "obsidian",
                "prismarine", "clay"
        };
        for (String mat : materials) itemName = itemName.replace(mat, "");

        // Dynamic color for the [Tier X] bracket based on level
        ChatFormatting tierColor = getTierColor(tierLevel);
        Style sharedStyle = Style.EMPTY.withColor(effect.color).withItalic(false);

        Component fullName = Component.empty()
                .append(Component.literal("[Tier " + roman + "] ")
                        .setStyle(Style.EMPTY.withColor(tierColor).withBold(true).withItalic(false)))
                .append(Component.literal(effect.prefixName + " ")
                        .setStyle(sharedStyle))
                .append(Component.literal(itemName.trim())
                        .setStyle(sharedStyle));

        itemStack.set(DataComponents.CUSTOM_NAME, fullName);
    }

    /**
     * Returns a dynamic color based on the tier level.
     * I-III: Low (Gray/White/Green)
     * IV-VI: Mid (Aqua/Blue/Purple)
     * VII-IX: High (Gold/Red)
     * X: Master (Light Purple/Bold)
     */
    private static ChatFormatting getTierColor(int tierLevel) {
        return switch (tierLevel) {
            case 1 -> ChatFormatting.GRAY;
            case 2 -> ChatFormatting.WHITE;
            case 3 -> ChatFormatting.GREEN;
            case 4 -> ChatFormatting.AQUA;
            case 5 -> ChatFormatting.BLUE;
            case 6 -> ChatFormatting.DARK_PURPLE;
            case 7 -> ChatFormatting.YELLOW; // Gold-like
            case 8 -> ChatFormatting.GOLD;
            case 9 -> ChatFormatting.RED;
            case 10 -> ChatFormatting.LIGHT_PURPLE; // The "Legendary" pink/magenta
            default -> ChatFormatting.GRAY;
        };
    }

    private static void addCustomLore(
            ItemStack itemStack, TierConfig.TierEffect effect,
            TierConfig.ItemCategory category, Map<Holder<Attribute>, Double> targetEffect) {

        List<Component> loreLines = new ArrayList<>();
        loreLines.add(Component.literal(""));
        loreLines.add(Component.literal("Modifier")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false)));

        for (Map.Entry<Holder<Attribute>, Double> entry : targetEffect.entrySet()) {
            String attrName = Component.translatable(
                    entry.getKey().value().getDescriptionId()).getString();
            double value = entry.getValue();
            String formatted = (value >= 0)
                    ? String.format("+%.2f %s", value, attrName)
                    : String.format("%.2f %s", value, attrName);
            loreLines.add(Component.literal(formatted)
                    .setStyle(Style.EMPTY.withColor(effect.color).withItalic(false)));
        }

        if (!effect.getStatusEffects().isEmpty() && !effect.getStatusEffectDescription().isEmpty()) {
            loreLines.add(Component.literal(effect.getStatusEffectDescription())
                    .setStyle(Style.EMPTY.withColor(effect.color).withItalic(false)));
        }

        itemStack.set(DataComponents.LORE, new ItemLore(loreLines));
    }

    private static void storeEffectTag(ItemStack itemStack, TierConfig.TierEffect effect) {
        String target = effect.getStatusEffectTarget();
        String combined = target.isEmpty()
                ? "claimed"
                : effect.getStatusEffectName() + "|"
                + effect.getStatusEffectTrigger() + "|"
                + target + "|"
                + effect.getStatusEffectTriggerChance() + "|"
                + effect.getStatusEffectDuration();


        // Convert particle list to a semicolon-separated string for NBT storage
        String particleData = effect.getParticles().stream()
                .map(p -> p.type + ":" + p.chance)
                .collect(Collectors.joining(";")); //[cite: 1]

        CustomData current = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData updated = current.update(nbt -> {
            CompoundTag origin = new CompoundTag();
            origin.putString("tierDrop", combined);
            origin.putString("particles", particleData); // Save particles here[cite: 1]
            nbt.put("tierdrop:origin", origin);
        });
        itemStack.set(DataComponents.CUSTOM_DATA, updated);
    }

    public static void addClaimedTag(ItemStack itemStack) {
        CustomData current = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData updated = current.update(nbt -> {
            CompoundTag origin = new CompoundTag();
            origin.putString("tierDrop", "claimed");
            nbt.put("tierdrop:origin", origin);
        });
        itemStack.set(DataComponents.CUSTOM_DATA, updated);
    }

    // -------------------------------------------------------------------------
    // Roman numerals
    // -------------------------------------------------------------------------

    private static String toRoman(int n) {
        String[] romans = {
                "", "I", "II", "III", "IV", "V",
                "VI", "VII", "VIII", "IX", "X"
        };
        return (n >= 1 && n < romans.length) ? romans[n] : Integer.toString(n);
    }


}
