package net.nian.tierdrop;

import java.util.List;
import java.util.Map;

/**
 * POJO model for tierdrop_config.json, parsed by Gson.
 */
public class TierDropConfig {
    public TierEquipment TierEquipment;

    public static class TierEquipment {
        public List<TierEquipmentConfig> Test;
        public List<TierEquipmentConfig> Armor;
        public List<TierEquipmentConfig> Weapon;
        public List<TierEquipmentConfig> RangeWeapon;
        public List<TierEquipmentConfig> MiningTool;
        public Boolean ApplyTiersOnCraftedEquipments = false;
        public Boolean AllowCraftedEquipmentsToGetHighTiers = false;
    }

    public static class TierEquipmentConfig {
        public String prefix;
        public String color;
        public int[] chance;
        public List<Map<String, List<Number>>> attributes;
        public List<TierParticleConfig> particles;
    }

    public static class TierParticleConfig {
        public String type;
        public double chance;
    }
}
