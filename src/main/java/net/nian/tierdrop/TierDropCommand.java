package net.nian.tierdrop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

/**
 * Registers /tierdrop commands.
 *
 * Commands:
 *   /tierdrop unclaim <item_name>
 *     — Removes the tierdrop NBT claim from all items matching <item_name>
 *       in YOUR inventory. Allows the item to receive a new tier.
 *
 *   /tierdrop unclaim <item_name> <player>
 *     — Same but targets another player (requires OP level 2).
 *
 *   /tierdrop unclaimall
 *     — Removes claim from ALL tiered items in your inventory.
 *
 *   /tierdrop unclaimall <player>
 *     — Same but targets another player (requires OP level 2).
 *
 * Usage examples:
 *   /tierdrop unclaim "diamond sword"
 *   /tierdrop unclaim iron_sword Steve
 *   /tierdrop unclaimall
 *   /tierdrop unclaimall Steve
 */
public class TierDropCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Entire /tierdrop command tree requires OP level 2
        dispatcher.register(
                Commands.literal("tierdrop")
                        .requires(src -> {
                            ServerPlayer player = src.getPlayer(); // returns null if not a player (e.g. console)
                            if (player == null) return true;       // console/server always allowed
                            return src.getServer().getPlayerList().isOp(player.nameAndId());
                        })

                        // ── /tierdrop unclaim <item_name> [player] ────────────────────
                        .then(Commands.literal("unclaim")
                                .then(Commands.argument("item_name", StringArgumentType.greedyString())
                                        // Self
                                        .executes(ctx -> executeUnclaim(ctx,
                                                null,
                                                StringArgumentType.getString(ctx, "item_name")))
                                        // Target another player
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> executeUnclaim(ctx,
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "item_name"))))
                                )
                        )

                        // ── /tierdrop unclaimall [player] ─────────────────────────────
                        .then(Commands.literal("unclaimall")
                                // Self
                                .executes(ctx -> executeUnclaimAll(ctx, null))
                                // Target another player
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> executeUnclaimAll(ctx,
                                                EntityArgument.getPlayer(ctx, "player"))))
                        )

                        // ── /tierdrop help ────────────────────────────────────────────
                        .then(Commands.literal("help")
                                .executes(TierDropCommand::executeHelp)
                        )
        );
    }

    // ── /tierdrop unclaim <item_name> [player] ────────────────────────────────

    private static int executeUnclaim(
            CommandContext<CommandSourceStack> ctx,
            ServerPlayer targetArg,
            String itemName
    ) {
        try {
            ServerPlayer player = resolvePlayer(ctx, targetArg);
            if (player == null) return 0;

            String searchName = itemName.toLowerCase().trim();
            int removed = 0;

            // Search entire inventory (main + armor + offhand)
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;

                // Match by item display name OR registry id
                String displayName = stack.getHoverName().getString().toLowerCase();
                String registryId  = stack.getItem().getDescriptionId().toLowerCase();

                boolean nameMatches = displayName.contains(searchName)
                        || registryId.contains(searchName)
                        || registryId.replace("item.", "").replace("block.", "")
                        .replace("minecraft.", "").contains(searchName);

                if (!nameMatches) continue;
                if (!hasTierClaim(stack)) continue;

                removeTierClaim(stack);
                removed++;

                TierDrop.LOGGER.info("[TierDrop|CMD] Unclaimed '{}' (slot {}) for player {}",
                        displayName, i, player.getName().getString());
            }

            if (removed > 0) {
                int finalRemoved = removed;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[TierDrop] Removed tier claim from " + finalRemoved + " item(s) matching \""
                                + itemName + "\" in " + player.getName().getString() + "'s inventory."
                ), false);
            } else {
                ctx.getSource().sendFailure(Component.literal(
                        "[TierDrop] No claimed tiered items matching \"" + itemName
                                + "\" found in " + player.getName().getString() + "'s inventory."
                ));
            }

            return removed;

        } catch (Exception e) {
            TierDrop.LOGGER.error("[TierDrop|CMD] unclaim error: {}", e.getMessage());
            ctx.getSource().sendFailure(Component.literal("[TierDrop] Command error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /tierdrop unclaimall [player] ─────────────────────────────────────────

    private static int executeUnclaimAll(
            CommandContext<CommandSourceStack> ctx,
            ServerPlayer targetArg
    ) {
        try {
            ServerPlayer player = resolvePlayer(ctx, targetArg);
            if (player == null) return 0;

            int removed = 0;

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if (!hasTierClaim(stack)) continue;

                String displayName = stack.getHoverName().getString();
                removeTierClaim(stack);
                removed++;

                TierDrop.LOGGER.info("[TierDrop|CMD] Unclaimed '{}' (slot {}) for player {}",
                        displayName, i, player.getName().getString());
            }

            if (removed > 0) {
                int finalRemoved = removed;
                ctx.getSource().sendSuccess(() -> Component.literal(
                        "[TierDrop] Removed tier claim from " + finalRemoved
                                + " item(s) in " + player.getName().getString() + "'s inventory."
                ), false);
            } else {
                ctx.getSource().sendFailure(Component.literal(
                        "[TierDrop] No claimed tiered items found in "
                                + player.getName().getString() + "'s inventory."
                ));
            }

            return removed;

        } catch (Exception e) {
            TierDrop.LOGGER.error("[TierDrop|CMD] unclaimall error: {}", e.getMessage());
            ctx.getSource().sendFailure(Component.literal("[TierDrop] Command error: " + e.getMessage()));
            return 0;
        }
    }

    // ── /tierdrop help ────────────────────────────────────────────────────────

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6[TierDrop Commands]\n" +
                        "§e/tierdrop unclaim <name>§r — Remove tier claim from items matching name\n" +
                        "§e/tierdrop unclaim <name> <player>§r — Same, targets another player (OP 2)\n" +
                        "§e/tierdrop unclaimall§r — Remove tier claim from ALL tiered items\n" +
                        "§e/tierdrop unclaimall <player>§r — Same, targets another player (OP 2)\n" +
                        "§7Item name can be partial — 'sword' matches 'diamond sword', etc."
        ), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resolves the target player.
     * If targetArg is null, uses the command source itself.
     */
    private static ServerPlayer resolvePlayer(
            CommandContext<CommandSourceStack> ctx,
            ServerPlayer targetArg
    ) {
        if (targetArg != null) return targetArg;

        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(
                    "[TierDrop] This command must be run by a player, or specify a target player."));
            return null;
        }
    }

    /**
     * Returns true if the item has a tierdrop:origin NBT tag.
     */
    private static boolean hasTierClaim(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        return data.copyTag().contains("tierdrop:origin");
    }

    /**
     * Removes the tierdrop:origin compound from the item's CUSTOM_DATA.
     * Leaves other custom data untouched.
     */
    private static void removeTierClaim(ItemStack stack) {
        CustomData current = stack.get(DataComponents.CUSTOM_DATA);
        if (current == null) return;

        CustomData updated = current.update(nbt -> nbt.remove("tierdrop:origin"));
        stack.set(DataComponents.CUSTOM_DATA, updated);
    }
}