package net.nian.tierdrop;

import net.minecraft.util.RandomSource;

/**
 * A min/max attribute range. Higher tiers land closer to {@code max}.
 */
public class MinMaxDouble {
    public final double min;
    public final double max;

    public MinMaxDouble(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double getRandom(RandomSource random, int tierLevel) {
        double range = max - min;
        double lowerBound = Math.min(max, min + (range * (tierLevel * 0.1)));
        double upperBound = Math.min(max, min + (range * ((tierLevel + 1) * 0.1)));
        return lowerBound + (upperBound - lowerBound) * random.nextDouble();
    }
}
