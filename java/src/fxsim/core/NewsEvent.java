package fxsim.core;

/** 一条经济新闻事件：瞬间冲击 + 之后一段时间的高波动、点差扩大。 */
public final class NewsEvent {
    public final String ccy;      // USD / EUR / GBP / JPY / AUD / CAD / CHF / NZD / XAU
    public final String title;
    public final double jump;     // 对数冲击，正数代表该货币走强
    public final long start;
    public final long end;
    public final boolean big;

    public NewsEvent(String ccy, String title, double jump, long start, long end, boolean big) {
        this.ccy = ccy;
        this.title = title;
        this.jump = jump;
        this.start = start;
        this.end = end;
        this.big = big;
    }

    public boolean core(String symbol) {
        return symbol.contains(ccy);
    }

    /** 事件剩余强度 0..1 */
    public double strength(long minute) {
        if (minute <= start) return 1.0;
        double p = (minute - start) / (double) Math.max(1, end - start);
        return Math.max(0, 1.0 - p);
    }

    public double volMult(long minute, boolean affected) {
        return 1.0 + (affected ? (big ? 3.4 : 2.2) : 0.55) * strength(minute);
    }

    public String headline(long minute) {
        String dir = jump > 0 ? "强于预期" : "弱于预期";
        return ccy + " · " + title + " " + dir;
    }
}
