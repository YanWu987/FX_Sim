package fxsim.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** 单个品种的实时状态：报价、点差、日内高低、各周期 K 线。 */
public final class PairState {
    public static final int MAX_SERIES = 900;

    public final Instrument ins;
    public double mid;
    public double spreadPips;

    public double dayOpen;
    public double prevClose;
    public double dayHigh;
    public double dayLow;
    /** 期货是否封在涨停/跌停 */
    public boolean limitUp;
    public boolean limitDown;

    public final EnumMap<Timeframe, ArrayDeque<Candle>> hist = new EnumMap<>(Timeframe.class);
    public final EnumMap<Timeframe, Candle> cur = new EnumMap<>(Timeframe.class);

    public PairState(Instrument ins) {
        this.ins = ins;
        this.mid = ins.startPrice;
        this.spreadPips = ins.spreadPips;
        this.dayOpen = this.prevClose = this.dayHigh = this.dayLow = mid;
        for (Timeframe tf : Timeframe.values()) {
            hist.put(tf, new ArrayDeque<>());
        }
    }

    public double bid() {
        return mid - spreadPips * ins.pipSize * 0.5;
    }

    public double ask() {
        return mid + spreadPips * ins.pipSize * 0.5;
    }

    /** 当日涨跌幅（相对开盘价） */
    public double dayChangePct() {
        return dayOpen == 0 ? 0 : (mid - dayOpen) / dayOpen * 100.0;
    }

    /** 每吃掉一分钟，更新所有周期的 K 线 */
    public void onTick(long minute, double price) {
        for (Timeframe tf : Timeframe.values()) {
            long bucket = Math.floorDiv(minute, tf.minutes) * (long) tf.minutes;
            Candle c = cur.get(tf);
            if (c == null || c.t != bucket) {
                if (c != null) {
                    ArrayDeque<Candle> q = hist.get(tf);
                    q.addLast(c);
                    while (q.size() > MAX_SERIES) q.pollFirst();
                }
                cur.put(tf, new Candle(bucket, price));
            } else {
                c.add(price);
            }
        }
    }

    /** 已收盘 K 线 + 当前正在走的一根 */
    public List<Candle> series(Timeframe tf) {
        List<Candle> out = new ArrayList<>(hist.get(tf));
        Candle c = cur.get(tf);
        if (c != null) out.add(c);
        return out;
    }

    public Candle last(Timeframe tf) {
        Candle c = cur.get(tf);
        if (c != null) return c;
        return hist.get(tf).peekLast();
    }
}
