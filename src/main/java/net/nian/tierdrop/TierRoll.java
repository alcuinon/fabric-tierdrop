package net.nian.tierdrop;

/**
 * How strongly one loot source rolls a tier.
 *
 * @param appliedLevel which config chance to use: 1 normal, 2 chest, 3 treasure
 * @param bonusChance extra weight added to low-chance tiers, before the cap in {@code ItemTierHandler}
 * @param minTierLevel added to the rolled 1–10 tier, then clamped
 */
public record TierRoll(int appliedLevel, int bonusChance, int minTierLevel) {
}
