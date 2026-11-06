package net.nian.tierdrop;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.nian.tierdrop.TierConfig.TierEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes a rolled tier onto an item: attributes, name, lore, and custom data.
 */
final class TierItemEditor {

    private static final String[] MATERIAL_WORDS = {
            "iron", "diamond", "netherite", "golden", "leather", "chainmail",
            "wooden", "stone", "copper", "emerald", "lapis", "obsidian",
            "prismarine", "clay"
    };

    private static final String[] ROMAN = {
            "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"
    };

    private TierItemEditor() {}

    static int apply(ItemStack stack, TierEffect effect, int minTierLevel, RandomSource random) {
        EquipmentSlot slot = ItemTierHandler.getRelevantSlot(stack);
        if (slot == null) return 0;

        int tierLevel = Math.clamp(random.nextInt(10) + 1 + minTierLevel, 1, 10);
        Map<Holder<Attribute>, Double> rolled = addAttributeModifiers(stack, effect, slot, tierLevel, random);
        setTierName(stack, effect, tierLevel);
        setLore(stack, effect, rolled);
        TierItemData.writeEffect(stack, effectTag(effect), particleTag(effect));
        return tierLevel;
    }

    private static Map<Holder<Attribute>, Double> addAttributeModifiers(
            ItemStack stack, TierEffect effect, EquipmentSlot slot, int tierLevel, RandomSource random) {

        Map<Holder<Attribute>, Double> rolled = new HashMap<>();
        ItemAttributeModifiers base = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);

        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }

        for (Map.Entry<Holder<Attribute>, MinMaxDouble> entry : effect.getAttributesForSlot(slot).entrySet()) {
            double amount = entry.getValue().getRandom(random, tierLevel);
            AttributeModifier modifier = new AttributeModifier(
                    modifierId(slot, entry.getKey()),
                    amount,
                    AttributeModifier.Operation.ADD_VALUE);
            builder.add(entry.getKey(), modifier, EquipmentSlotGroup.bySlot(slot));
            rolled.put(entry.getKey(), amount);
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return rolled;
    }

    private static Identifier modifierId(EquipmentSlot slot, Holder<Attribute> attribute) {
        String attributeKey = attribute.value().getDescriptionId()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        String path = slot.name().toLowerCase(Locale.ROOT) + "_" + attributeKey;
        return Identifier.fromNamespaceAndPath(TierDrop.MOD_ID, path);
    }

    private static void setTierName(ItemStack stack, TierEffect effect, int tierLevel) {
        Style nameStyle = Style.EMPTY.withColor(effect.color).withItalic(false);
        Component name = Component.empty()
                .append(Component.literal("[Tier " + toRoman(tierLevel) + "] ")
                        .setStyle(Style.EMPTY.withColor(tierColor(tierLevel)).withBold(true).withItalic(false)))
                .append(Component.literal(effect.prefixName + " ").setStyle(nameStyle))
                .append(Component.literal(stripMaterial(stack)).setStyle(nameStyle));
        stack.set(DataComponents.CUSTOM_NAME, name);
    }

    private static String stripMaterial(ItemStack stack) {
        String name = stack.getItem().getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT);
        for (String material : MATERIAL_WORDS) {
            name = name.replace(material, "");
        }
        return name.trim();
    }

    private static ChatFormatting tierColor(int tierLevel) {
        return switch (tierLevel) {
            case 1 -> ChatFormatting.GRAY;
            case 2 -> ChatFormatting.WHITE;
            case 3 -> ChatFormatting.GREEN;
            case 4 -> ChatFormatting.AQUA;
            case 5 -> ChatFormatting.BLUE;
            case 6 -> ChatFormatting.DARK_PURPLE;
            case 7 -> ChatFormatting.YELLOW;
            case 8 -> ChatFormatting.GOLD;
            case 9 -> ChatFormatting.RED;
            case 10 -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }

    private static void setLore(ItemStack stack, TierEffect effect, Map<Holder<Attribute>, Double> rolled) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(""));
        lines.add(Component.literal("Modifier")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true).withItalic(false)));

        for (Map.Entry<Holder<Attribute>, Double> entry : rolled.entrySet()) {
            String attributeName = Component.translatable(entry.getKey().value().getDescriptionId()).getString();
            double value = entry.getValue();
            String formatted = value >= 0
                    ? String.format("+%.2f %s", value, attributeName)
                    : String.format("%.2f %s", value, attributeName);
            lines.add(Component.literal(formatted)
                    .setStyle(Style.EMPTY.withColor(effect.color).withItalic(false)));
        }

        if (!effect.getStatusEffects().isEmpty() && !effect.getStatusEffectDescription().isEmpty()) {
            lines.add(Component.literal(effect.getStatusEffectDescription())
                    .setStyle(Style.EMPTY.withColor(effect.color).withItalic(false)));
        }

        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static String effectTag(TierEffect effect) {
        String target = effect.getStatusEffectTarget();
        if (target.isEmpty()) return "claimed";
        return effect.getStatusEffectName() + "|"
                + effect.getStatusEffectTrigger() + "|"
                + target + "|"
                + effect.getStatusEffectTriggerChance() + "|"
                + effect.getStatusEffectDuration();
    }

    private static String particleTag(TierEffect effect) {
        return effect.getParticles().stream()
                .map(particle -> particle.type + ":" + particle.chance)
                .collect(Collectors.joining(";"));
    }

    private static String toRoman(int tierLevel) {
        if (tierLevel >= 1 && tierLevel < ROMAN.length) return ROMAN[tierLevel];
        return Integer.toString(tierLevel);
    }
}
