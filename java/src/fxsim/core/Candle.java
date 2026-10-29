package fxsim.core;

/** 一根 K 线。t 是开始时间的"游戏分钟序号"。 */
public final class Candle {
    public final long t;
    public double o, h, l, c;

    public Candle(long t, double price) {
        this.t = t;
        this.o = this.h = this.l = this.c = price;
    }

    public Candle(long t, double o, double h, double l, double c) {
        this.t = t;
        this.o = o;
        this.h = h;
        this.l = l;
        this.c = c;
    }

    public void add(double price) {
        h = Math.max(h, price);
        l = Math.min(l, price);
        c = price;
    }

    public boolean up() {
        return c >= o;
    }
}
