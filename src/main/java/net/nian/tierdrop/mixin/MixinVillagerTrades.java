package net.nian.tierdrop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.nian.tierdrop.ItemTierHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantResultSlot.class)
public class MixinVillagerTrades {

    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void onTakeTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || stack.isEmpty()) return;

        // Check if the item is eligible for a tier (Weapon, Armor, Tool)
        if (!ItemTierHandler.shouldApplyTier(stack)) return;

        int appliedLevel = 2; // Default Merchant Level
        int addChance = 0;
        int minTier = -2;     // Standard baseline

        // 1. Scale quality based on Villager Profession Level
        if (this.merchant instanceof Villager villager) {
            int villagerLevel = villager.getVillagerData().level(); // 1 to 5

            // Master Villagers (Level 5) give much better gear
            addChance = villagerLevel * 5;
            minTier = villagerLevel - 3; // Level 5 Villager = Min Tier II
            appliedLevel = villagerLevel >= 4 ? 3 : 2;
        }

        // 2. Bonus for Wandering Traders (They are rare, so give them a boost)
        else if (this.merchant instanceof WanderingTrader) {
            appliedLevel = 3;
            addChance = 15;
            minTier = 0; // Guaranteed Tier I+
        }

        // 3. Hero of the Village Bonus (Optional: rewards the player for saving the village)
        if (serverPlayer.hasEffect(net.minecraft.world.effect.MobEffects.HERO_OF_THE_VILLAGE)) {
            addChance += 10;
            minTier += 1;
        }

        ItemTierHandler.applyTierIfEligible(stack, serverPlayer, appliedLevel, addChance, minTier);
    }
}