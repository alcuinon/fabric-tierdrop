package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.nian.tierdrop.ItemTierHandler;
import net.nian.tierdrop.TierRoll;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public class MixinVillagerTrades {

    @Shadow
    @Final
    private Merchant merchant;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;
        if (!ItemTierHandler.shouldApplyTier(stack)) return;

        TierRoll roll = rollFor(serverPlayer);
        ItemTierHandler.applyTierIfEligible(stack, serverPlayer, roll);
    }

    private TierRoll rollFor(ServerPlayer player) {
        TierRoll roll = new TierRoll(2, 0, -2);
        if (merchant instanceof Villager villager) {
            int level = villager.getVillagerData().level();
            roll = new TierRoll(level >= 4 ? 3 : 2, level * 5, level - 3);
        } else if (merchant instanceof WanderingTrader) {
            roll = new TierRoll(3, 15, 0);
        }

        if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            roll = new TierRoll(roll.appliedLevel(), roll.bonusChance() + 10, roll.minTierLevel() + 1);
        }
        return roll;
    }
}
