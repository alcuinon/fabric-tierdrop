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

import java.util.Locale;
import java.util.function.Predicate;

/**
 * Operator commands that clear the tier claim on items so they can roll again.
 * Multi-word item names need quotes: {@code /tierdrop unclaim "diamond sword"}.
 */
public final class TierDropCommand {

    private TierDropCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tierdrop")
                .requires(TierDropCommand::isAllowed)
                .then(Commands.literal("unclaim")
                        .then(Commands.argument("item_name", StringArgumentType.string())
                                .executes(ctx -> unclaim(ctx, null, StringArgumentType.getString(ctx, "item_name")))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> unclaim(
                                                ctx,
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "item_name"))))))
                .then(Commands.literal("unclaimall")
                        .executes(ctx -> unclaimAll(ctx, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> unclaimAll(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("help")
                        .executes(TierDropCommand::help)));
    }

    private static boolean isAllowed(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return true;
        return source.getServer().getPlayerList().isOp(player.nameAndId());
    }

    private static int unclaim(CommandContext<CommandSourceStack> ctx, ServerPlayer target, String itemName) {
        ServerPlayer player = resolvePlayer(ctx, target);
        if (player == null) return 0;

        String search = itemName.toLowerCase(Locale.ROOT).trim();
        int removed = clearMatching(player, stack -> nameMatches(stack, search));
        if (removed > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TierDrop] Removed tier claim from " + removed + " item(s) matching \""
                            + itemName + "\" in " + player.getName().getString() + "'s inventory."
            ), false);
        } else {
            ctx.getSource().sendFailure(Component.literal(
                    "[TierDrop] No claimed tiered items matching \"" + itemName
                            + "\" found in " + player.getName().getString() + "'s inventory."
            ));
        }
        return removed;
    }

    private static int unclaimAll(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        ServerPlayer player = resolvePlayer(ctx, target);
        if (player == null) return 0;

        int removed = clearMatching(player, stack -> true);
        if (removed > 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[TierDrop] Removed tier claim from " + removed
                            + " item(s) in " + player.getName().getString() + "'s inventory."
            ), false);
        } else {
            ctx.getSource().sendFailure(Component.literal(
                    "[TierDrop] No claimed tiered items found in "
                            + player.getName().getString() + "'s inventory."
            ));
        }
        return removed;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6[TierDrop Commands]\n"
                        + "§e/tierdrop unclaim <name> [player]§r — Clear the tier claim on matching items\n"
                        + "§e/tierdrop unclaimall [player]§r — Clear the tier claim on every tiered item\n"
                        + "§7Quote names that contain spaces. 'sword' matches 'diamond sword'."
        ), false);
        return 1;
    }

    private static int clearMatching(ServerPlayer player, Predicate<ItemStack> match) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !match.test(stack) || !TierItemData.hasOrigin(stack)) continue;

            String displayName = stack.getHoverName().getString();
            TierItemData.removeOrigin(stack);
            removed++;
            TierDrop.LOGGER.info("Unclaimed '{}' (slot {}) for {}", displayName, slot, player.getName().getString());
        }
        return removed;
    }

    private static boolean nameMatches(ItemStack stack, String search) {
        String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        String registryId = stack.getItem().getDescriptionId().toLowerCase(Locale.ROOT);
        String simpleId = registryId.replace("item.", "").replace("block.", "").replace("minecraft.", "");
        return displayName.contains(search) || registryId.contains(search) || simpleId.contains(search);
    }

    private static ServerPlayer resolvePlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        if (target != null) return target;
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(
                    "[TierDrop] This command must be run by a player, or specify a target player."));
            return null;
        }
    }
}
