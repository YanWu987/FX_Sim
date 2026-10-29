package fxsim.core;

/**
 * 可保存状态的伪随机数发生器（xorshift64*）。
 * 用它替代 java.util.Random，是为了让存档能精确恢复随机序列——
 * 读档之后行情会沿着原来的轨迹继续走，而不是重新洗牌。
 */
public final class Rng {
    private long s;
    private boolean hasGauss;
    private double gaussVal;

    public Rng(long seed) {
        setState(seed);
    }

    public long state() {
        return s;
    }

    public void setState(long v) {
        s = (v == 0) ? 0x9E3779B97F4A7C15L : v;
    }

    public boolean hasCachedGauss() {
        return hasGauss;
    }

    public double cachedGauss() {
        return gaussVal;
    }

    public void setCachedGauss(boolean has, double v) {
        hasGauss = has;
        gaussVal = v;
    }

    private long nextLong() {
        long x = s;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        s = x;
        return x * 0x2545F4914F6CDD1DL;
    }

    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    public boolean nextBoolean() {
        return (nextLong() & 1L) == 0;
    }

    public int nextInt(int bound) {
        return (int) Math.floorMod(nextLong(), (long) bound);
    }

    public double nextGaussian() {
        if (hasGauss) {
            hasGauss = false;
            return gaussVal;
        }
        double u, v, r;
        do {
            u = nextDouble() * 2 - 1;
            v = nextDouble() * 2 - 1;
            r = u * u + v * v;
        } while (r >= 1 || r == 0);
        double mul = Math.sqrt(-2 * Math.log(r) / r);
        gaussVal = v * mul;
        hasGauss = true;
        return u * mul;
    }
}
