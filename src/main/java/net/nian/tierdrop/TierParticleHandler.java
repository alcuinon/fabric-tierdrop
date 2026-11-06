package net.nian.tierdrop;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/**
 * Spawns the particle aura stored on equipped tier items.
 */
public final class TierParticleHandler {

    private TierParticleHandler() {}

    public static void spawnAuraForPlayer(ServerPlayer player) {
        if (player.level().isClientSide()) return;

        ServerLevel world = (ServerLevel) player.level();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) spawnForItem(player, world, stack, slot);
        }
    }

    private static void spawnForItem(ServerPlayer player, ServerLevel world, ItemStack stack, EquipmentSlot slot) {
        String raw = TierItemData.particles(stack);
        if (raw.isEmpty()) return;

        for (String entry : raw.split(";")) {
            String[] parts = entry.trim().split(":");
            if (parts.length != 2) continue;
            try {
                double chance = Double.parseDouble(parts[1].trim());
                spawn(player, world, parts[0].trim().toLowerCase(Locale.ROOT), chance, slot);
            } catch (NumberFormatException ignored) {
                // Bad config data should not spam the tick loop.
            }
        }
    }

    private static void spawn(ServerPlayer player, ServerLevel world, String name, double chance, EquipmentSlot slot) {
        var found = BuiltInRegistries.PARTICLE_TYPE.get(Identifier.withDefaultNamespace(name));
        if (found.isEmpty() || !(found.get().value() instanceof ParticleOptions particle)) return;
        if (player.getRandom().nextDouble() >= chance) return;

        double orbitTime = (player.tickCount + world.getGameTime()) * 0.05;
        Vec3 pos = wobble(player, positionFor(player, slot, orbitTime));
        world.sendParticles(particle, pos.x, pos.y, pos.z, 1, 0.01, 0.01, 0.01, 0.0);
    }

    private static Vec3 positionFor(ServerPlayer player, EquipmentSlot slot, double orbitTime) {
        return switch (slot) {
            case HEAD -> headPosition(player);
            case CHEST -> chestPosition(player);
            case LEGS -> legsPosition(player);
            case FEET -> feetPosition(player);
            case MAINHAND -> mainHandPosition(player);
            case OFFHAND -> offHandPosition(player, orbitTime);
            default -> new Vec3(player.getX(), player.getY(), player.getZ());
        };
    }

    private static Vec3 wobble(ServerPlayer player, Vec3 pos) {
        double spread = 0.03;
        double time = player.tickCount * 0.1;
        return pos.add(
                (player.getRandom().nextDouble() - 0.5) * spread + Math.cos(time) * 0.02,
                (player.getRandom().nextDouble() - 0.5) * spread,
                (player.getRandom().nextDouble() - 0.5) * spread + Math.sin(time) * 0.02);
    }

    private static Vec3 headPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 base = player.getEyePosition().add(0, 0.25, 0);
        double radius = 0.35;
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = new Vec3(0, 1, 0);

        Vec3 pos;
        int attempts = 0;
        do {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double distance = player.getRandom().nextDouble() * radius;
            pos = base
                    .add(right.scale(Math.cos(angle) * distance))
                    .add(up.scale((player.getRandom().nextDouble() - 0.5) * 0.25))
                    .add(look.scale((player.getRandom().nextDouble() - 0.3) * -0.15));
            attempts++;
        } while (look.dot(pos.subtract(base).normalize()) > 0.25 && attempts < 5);
        return pos;
    }

    private static Vec3 chestPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 base = player.getEyePosition().add(0, -0.45, 0);
        double side = player.getRandom().nextBoolean() ? 1 : -1;
        double spread = 0.08;

        return base
                .add(right.scale(0.55 * side))
                .add(0, 0.15, 0)
                .add(
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread);
    }

    private static Vec3 legsPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        double side = player.getRandom().nextBoolean() ? 1 : -1;
        return player.getPosition(1.0f)
                .add(0, 0.6, 0)
                .add(right.scale(0.3 * side))
                .add(0, (player.getRandom().nextDouble() - 0.5) * 0.3, 0);
    }

    private static Vec3 feetPosition(ServerPlayer player) {
        double radius = 0.2;
        return player.getPosition(1.0f)
                .add(0, 0.1, 0)
                .add(
                        (player.getRandom().nextDouble() - 0.5) * radius,
                        0,
                        (player.getRandom().nextDouble() - 0.5) * radius);
    }

    private static Vec3 mainHandPosition(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        double spread = 0.04;
        return player.getEyePosition()
                .add(look.scale(0.55))
                .add(right.scale(0.35))
                .add(0, -0.55, 0)
                .add(
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread);
    }

    private static Vec3 offHandPosition(ServerPlayer player, double orbitTime) {
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        double orbit = Math.sin(orbitTime) * 0.15;
        return player.getEyePosition()
                .add(right.scale(-0.35))
                .add(look.scale(0.4))
                .add(0, -0.4, 0)
                .add(right.scale(-orbit));
    }
}
