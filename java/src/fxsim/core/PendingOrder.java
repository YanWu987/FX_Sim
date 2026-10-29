package fxsim.core;

/** 挂单：限价单 / 止损单 */
public final class PendingOrder {
    public enum Type {
        LIMIT("限价"), STOP("突破");
        public final String cn;
        Type(String cn) { this.cn = cn; }
    }

    public final int id;
    public final String symbol;
    public final Side side;
    public final double lots;
    public final Type type;
    public final double trigger;
    public final long created;
    public final double sl;
    public final double tp;

    public PendingOrder(int id, String symbol, Side side, double lots, Type type, double trigger,
                        long created, double sl, double tp) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.lots = lots;
        this.type = type;
        this.trigger = trigger;
        this.created = created;
        this.sl = sl;
        this.tp = tp;
    }

    public boolean hit(MarketEngine e) {
        PairState p = e.pair(symbol);
        double ask = p.ask(), bid = p.bid();
        if (type == Type.LIMIT) {
            return side == Side.BUY ? ask <= trigger : bid >= trigger;
        }
        return side == Side.BUY ? ask >= trigger : bid <= trigger;
    }
}
