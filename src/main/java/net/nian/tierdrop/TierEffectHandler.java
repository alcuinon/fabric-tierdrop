package net.nian.tierdrop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.ArmorMaterial;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles periodic on-equip status effect triggers for tier items.
 *
 * 26.1 mapping changes:
 *  - ServerWorld              → ServerLevel
 *  - ServerPlayerEntity       → ServerPlayer
 *  - DataComponentTypes       → DataComponents
 *  - NbtComponent             → CustomData
 *  - StatusEffects            → MobEffects
 *  - StatusEffectInstance     → MobEffectInstance
 */
public class TierEffectHandler {

    private static final Map<UUID, Long> lastEffectTime = new HashMap<>();

    public static void register() {
        ServerTickEvents.START_LEVEL_TICK.register(TierEffectHandler::handlePlayerEffects);
    }

    private static void handlePlayerEffects(ServerLevel world) {
//        long currentTime = world.getGameTime();
//
//        for (ServerPlayer player : world.players()) {
//            UUID playerId = player.getUUID();
//            long lastTime = lastEffectTime.getOrDefault(playerId, 0L);
//            if (currentTime < lastTime) continue;
//
//            for (ItemStack stack : player.getInventory()) { //check here, getArmorSlot is not working
//
//                if (stack.isEmpty()) continue;
//
//                CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
//                if (customData == null) continue;
//
//                Optional<CompoundTag> inner = customData.copyTag().getCompound("tierdrop:origin");
//                String origin = String.valueOf(inner.get().getString("tierDrop"));
//                if (origin.isEmpty()) continue;
//
//                String[] parts = origin.split("\\|");
//                if (parts.length != 5) continue;
//
//                String effectName    = parts[0];
//                String trigger       = parts[1];
//                // parts[2] = target (unused currently — always "player")
//                int triggerChance    = Integer.parseInt(parts[3]);
//                int duration         = Integer.parseInt(parts[4]) * 20;
//
//                var status = switch (effectName) {
//                    case "REGENERATION"      -> MobEffects.REGENERATION;
//                    case "FIRE_RESISTANCE"   -> MobEffects.FIRE_RESISTANCE;
//                    case "HERO_OF_THE_VILLAGE" -> MobEffects.HERO_OF_THE_VILLAGE;
//                    default                  -> MobEffects.SATURATION;
//                };
//
//                boolean triggered = false;
//                float healthFraction = player.getHealth() / player.getMaxHealth();
//
//                if (trigger.equals("lowhealth") && healthFraction < 1.0f) {
//                    if (Math.random() * 100 < triggerChance) {
//                        player.addEffect(new MobEffectInstance(status, duration, 1, true, false));
//                        triggered = true;
//                    }
//                } else if (trigger.equals("fullhealth") && healthFraction >= 1.0f) {
//                    if (Math.random() * 100 < triggerChance) {
//                        player.addEffect(new MobEffectInstance(status, duration, 1, true, false));
//                        triggered = true;
//                    }
//                }
//
//                if (triggered) {
//                    lastEffectTime.put(playerId, currentTime + duration + 200L);
//                    break; // one effect trigger per tick cycle is enough
//                }
//            }
//        }
    }
}
