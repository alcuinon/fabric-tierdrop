package net.nian.tierdrop;

import java.util.Random;

/**
 * Represents a min/max range for attribute values, scaled by tier level (1-10).
 */
public class MinMaxDouble {
    public final double min;
    public final double max;

    public MinMaxDouble(double min, double max) {
        this.min = min;
        this.max = max;
    }

    /**
     * Returns a random value within the sub-range determined by tierLevel (1–10).
     * Higher tier = values closer to max.
     */
    public double getRandom(Random random, int tierLevel) {
        double range = max - min;
        double lowerBound = min + (range * (tierLevel * 0.1));
        double upperBound = min + (range * ((tierLevel + 1) * 0.1));

        upperBound = Math.min(upperBound, max);
        lowerBound = Math.min(lowerBound, max);

        return lowerBound + (upperBound - lowerBound) * random.nextDouble();
    }
}
