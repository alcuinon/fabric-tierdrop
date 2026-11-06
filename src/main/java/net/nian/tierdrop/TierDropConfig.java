package net.nian.tierdrop;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON model for {@code config/tierdrop_config.json}.
 * Field names stay camelCase; {@link SerializedName} keeps the existing file keys.
 */
public class TierDropConfig {

    @SerializedName("TierEquipment")
    public TierEquipment tierEquipment = new TierEquipment();

    public static class TierEquipment {
        @SerializedName("Test")
        public List<TierEquipmentConfig> test = new ArrayList<>();

        @SerializedName("Armor")
        public List<TierEquipmentConfig> armor = new ArrayList<>();

        @SerializedName("Weapon")
        public List<TierEquipmentConfig> weapon = new ArrayList<>();

        @SerializedName("RangeWeapon")
        public List<TierEquipmentConfig> rangeWeapon = new ArrayList<>();

        @SerializedName("MiningTool")
        public List<TierEquipmentConfig> miningTool = new ArrayList<>();

        @SerializedName("ApplyTiersOnCraftedEquipments")
        public boolean applyTiersOnCraftedEquipments = false;

        @SerializedName("AllowCraftedEquipmentsToGetHighTiers")
        public boolean allowCraftedEquipmentsToGetHighTiers = false;

        /** When false, hostile mobs keep their normal equipment. */
        @SerializedName("EnableMobTiers")
        public boolean enableMobTiers = false;

        void ensureLists() {
            if (test == null) test = new ArrayList<>();
            if (armor == null) armor = new ArrayList<>();
            if (weapon == null) weapon = new ArrayList<>();
            if (rangeWeapon == null) rangeWeapon = new ArrayList<>();
            if (miningTool == null) miningTool = new ArrayList<>();
        }
    }

    public static class TierEquipmentConfig {
        public String prefix;
        /** Rarity name: common, uncommon, rare, epic, legendary, unique. Falls back to {@code prefix}. */
        public String tier;
        public String color;
        public int[] chance;
        /** Each entry is one slot mapped to {@code [attribute name, min, max]}. */
        public List<Map<String, List<Object>>> attributes;
        public List<TierParticleConfig> particles;
    }

    public static class TierParticleConfig {
        public String type;
        public double chance;
    }
}
