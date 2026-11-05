package net.nian.tierdrop;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public class TierParticleHandler {

    public static void spawnAuraForPlayer(ServerPlayer player) {
        // Only run on the server side
        if (player.level().isClientSide()) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            processItemParticles(player, (ServerLevel) player.level(), stack, slot);
        }
    }

    private static void processItemParticles(ServerPlayer player, ServerLevel world, ItemStack stack, EquipmentSlot slot) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;

        CompoundTag rootTag = data.copyTag();
        if (!rootTag.contains("tierdrop:origin")) return;

        Optional<CompoundTag> origin = data.copyTag().getCompound("tierdrop:origin");
        if (origin.isEmpty() || !origin.get().contains("particles")) return;
        String particleString = origin.get().getString("particles").toString();

        if (!particleString.isEmpty()) {
            String cleanParticleEntries = particleString.replace("Optional[", "")
                    .replace("]", "")
                    .trim()
                    .toLowerCase();

            String[] particleEntries = cleanParticleEntries.split(";");
            for (String entry : particleEntries) {
                String[] parts = entry.split(":");
                if (parts.length == 2) {
                    String type = parts[0];
                    double chance = Double.parseDouble(parts[1]);
                    broadcastParticle(player, world, type, chance, slot);
                }
            }
        }
    }

    private static void broadcastParticle(ServerPlayer player, ServerLevel world, String name, double chance, EquipmentSlot slot) {
        var particleTypeOptional = BuiltInRegistries.PARTICLE_TYPE.get(
                Identifier.withDefaultNamespace(name)
        );

        if (particleTypeOptional.isEmpty()) return;
        if (player.getRandom().nextDouble() >= chance) return;

        var particleOptions = (ParticleOptions) particleTypeOptional.get().value();

        // Time-based rotation (smooth orbit)
        double time = (player.tickCount + world.getGameTime()) * 0.05;


        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        switch (slot) {

            // 🪖 HEAD (floating orbit above head)
            case HEAD -> {
                Vec3 look = player.getLookAngle().normalize();

                // upward head anchor
                Vec3 base = player.getEyePosition().add(0, 0.25, 0);

                double radius = 0.35;

                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
                Vec3 up = new Vec3(0, 1, 0);

                Vec3 pos;
                int attempts = 0;

                do {
                    double angle = player.getRandom().nextDouble() * Math.PI * 2;
                    double r = player.getRandom().nextDouble() * radius;

                    // ring around head
                    pos = base
                            .add(right.scale(Math.cos(angle) * r))
                            .add(up.scale((player.getRandom().nextDouble() - 0.5) * 0.25))
                            .add(look.scale((player.getRandom().nextDouble() - 0.3) * -0.15)); // slight backward bias

                    attempts++;

                    // ❌ reject particles in front of face
                } while (look.dot(pos.subtract(base).normalize()) > 0.25 && attempts < 5);

                x = pos.x;
                y = pos.y;
                z = pos.z;
            }

            // 🛡️ CHEST (front orbit, outside armor)
            case CHEST -> {
                Vec3 look = player.getLookAngle();
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();

                // 🔥 anchor ABOVE chest (upper torso / shoulder line)
                Vec3 base = player.getEyePosition()
                        .add(0, -0.45, 0);

                // 👉 force particles OUTSIDE body (key fix)
                double sideOffset = 0.55;   // push outside shoulder
                double upOffset = 0.15;     // slightly above chest line

                // only left or right shoulder (not front/back)
                double side = player.getRandom().nextBoolean() ? 1 : -1;

                Vec3 pos = base
                        .add(right.scale(sideOffset * side))
                        .add(0, upOffset, 0);

                // small spread so it feels natural
                double spread = 0.08;
                pos = pos.add(
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread
                );

                x = pos.x;
                y = pos.y;
                z = pos.z;
            }

            // 👖 LEGS (lower orbit)
            case LEGS -> {
                Vec3 look = player.getLookAngle();
                Vec3 right = look.cross(new Vec3(0,1,0)).normalize();

                Vec3 base = player.getPosition(1.0f).add(0, 0.6, 0);

                double side = 0.3;

                double sideDir = player.getRandom().nextBoolean() ? 1 : -1;

                Vec3 pos = base
                        .add(right.scale(side * sideDir))
                        .add(0, (player.getRandom().nextDouble() - 0.5) * 0.3, 0);

                x = pos.x;
                y = pos.y;
                z = pos.z;
            }

            // 👢 FEET (small subtle orbit)
            case FEET -> {
                Vec3 base = player.getPosition(1.0f).add(0, 0.1, 0);

                double radius = 0.2;

                Vec3 randomOffset = new Vec3(
                        (player.getRandom().nextDouble() - 0.5) * radius,
                        0,
                        (player.getRandom().nextDouble() - 0.5) * radius
                );

                Vec3 pos = base.add(randomOffset);

                x = pos.x;
                y = pos.y;
                z = pos.z;
            }

            // ✋ MAIN HAND (rotating around hand)
            case MAINHAND -> {
                Vec3 look = player.getLookAngle();
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();

                Vec3 base = player.getEyePosition()
                        .add(look.scale(0.55))
                        .add(right.scale(0.35)) // ⬅️ increased from ~0.25 → pushes further right
                        .add(0, -0.55, 0);

                double spread = 0.04;

                Vec3 randomOffset = new Vec3(
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread,
                        (player.getRandom().nextDouble() - 0.5) * spread
                );

                Vec3 pos = base.add(randomOffset);

                x = pos.x;
                y = pos.y;
                z = pos.z;
            }

            // 🤚 OFFHAND (mirrored)
            case OFFHAND -> {
                double orbit = Math.sin(time) * 0.15;

                Vec3 look = player.getLookAngle();
                Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();

                Vec3 handPos = player.getEyePosition()
                        .add(right.scale(-0.35))
                        .add(look.scale(0.4))
                        .add(0, -0.4, 0)
                        .add(right.scale(-orbit));

                x = handPos.x;
                y = handPos.y;
                z = handPos.z;
            }
        }

        // ✨ Small randomness (prevents stiff look)
        double spread = 0.03;
        x += (player.getRandom().nextDouble() - 0.5) * spread;
        y += (player.getRandom().nextDouble() - 0.5) * spread;
        z += (player.getRandom().nextDouble() - 0.5) * spread;

        // 🌊 ADD ORBIT HERE
        time = player.tickCount * 0.1;
        x += Math.cos(time) * 0.02;
        z += Math.sin(time) * 0.02;

        // 🚀 Spawn particle
        world.sendParticles(
                particleOptions,
                x, y, z,
                1,          // count (keep low for smoothness)
                0.01, 0.01, 0.01,
                0.0
        );
    }
}