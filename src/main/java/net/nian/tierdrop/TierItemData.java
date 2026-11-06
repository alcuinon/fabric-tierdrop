package net.nian.tierdrop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Reads and writes the {@code tierdrop:origin} custom-data tag.
 */
public final class TierItemData {

    private static final String ORIGIN_KEY = "tierdrop:origin";
    private static final String TIER_KEY = "tierDrop";
    private static final String PARTICLES_KEY = "particles";

    private TierItemData() {}

    public static boolean hasTier(ItemStack stack) {
        return readString(stack, TIER_KEY).isPresent();
    }

    public static boolean hasOrigin(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(ORIGIN_KEY);
    }

    public static String particles(ItemStack stack) {
        return readString(stack, PARTICLES_KEY).orElse("");
    }

    /** Marks an item so it will not be rolled again. Existing tier data is left in place. */
    public static void markClaimed(ItemStack stack) {
        if (hasTier(stack)) return;
        editOrigin(stack, origin -> origin.putString(TIER_KEY, "claimed"));
    }

    public static void removeOrigin(ItemStack stack) {
        CustomData current = stack.get(DataComponents.CUSTOM_DATA);
        if (current == null) return;
        stack.set(DataComponents.CUSTOM_DATA, current.update(nbt -> nbt.remove(ORIGIN_KEY)));
    }

    static void writeEffect(ItemStack stack, String tierValue, String particleData) {
        editOrigin(stack, origin -> {
            origin.putString(TIER_KEY, tierValue);
            origin.putString(PARTICLES_KEY, particleData);
        });
    }

    private static Optional<String> readString(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        String value = data.copyTag().getCompoundOrEmpty(ORIGIN_KEY).getStringOr(key, "");
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    private static void editOrigin(ItemStack stack, Consumer<CompoundTag> editor) {
        CustomData current = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CustomData updated = current.update(nbt -> {
            CompoundTag origin = nbt.getCompoundOrEmpty(ORIGIN_KEY);
            editor.accept(origin);
            nbt.put(ORIGIN_KEY, origin);
        });
        stack.set(DataComponents.CUSTOM_DATA, updated);
    }
}
