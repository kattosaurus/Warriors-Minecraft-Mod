package com.katt.warriors.worldgen;

/**
 * Small, fixed-seed gradient noise used by the clanlands terrain.
 *
 * <p>The clanlands are the same for every world, so the noise is a pure
 * function of its inputs: no world seed, no state, safe to call from any
 * thread while chunks generate.</p>
 */
final class ClanNoise {

    private static final double[][] GRADIENTS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {0.70710678, 0.70710678}, {-0.70710678, 0.70710678},
            {0.70710678, -0.70710678}, {-0.70710678, -0.70710678}
    };

    private ClanNoise() {}

    private static int hash(int x, int z, int seed) {
        int h = x * 0x27d4eb2d ^ z * 0x165667b1 ^ seed * 0x9e3779b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double gradient(int hash, double dx, double dz) {
        double[] g = GRADIENTS[hash & 7];
        return g[0] * dx + g[1] * dz;
    }

    /** Perlin noise, roughly in [-1, 1]. */
    static double perlin(double x, double z, int seed) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = x - x0;
        double fz = z - z0;
        double u = fade(fx);
        double v = fade(fz);

        double n00 = gradient(hash(x0, z0, seed), fx, fz);
        double n10 = gradient(hash(x0 + 1, z0, seed), fx - 1.0, fz);
        double n01 = gradient(hash(x0, z0 + 1, seed), fx, fz - 1.0);
        double n11 = gradient(hash(x0 + 1, z0 + 1, seed), fx - 1.0, fz - 1.0);

        double nx0 = n00 + u * (n10 - n00);
        double nx1 = n01 + u * (n11 - n01);
        return (nx0 + v * (nx1 - nx0)) * 1.4142;
    }

    /** Fractal noise (several octaves), normalised to roughly [-1, 1]. */
    static double fbm(double x, double z, int octaves, int seed) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            sum += perlin(x * frequency, z * frequency, seed + i * 131) * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    /** Ridged noise in [0, 1]: sharp crests, good for mountain ranges. */
    static double ridged(double x, double z, int octaves, int seed) {
        double sum = 0.0;
        double amplitude = 1.0;
        double total = 0.0;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(perlin(x * frequency, z * frequency, seed + i * 197));
            sum += n * n * amplitude;
            total += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return sum / total;
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Smoothstep: 0 below {@code edge0}, 1 above {@code edge1}. */
    static double smooth(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}