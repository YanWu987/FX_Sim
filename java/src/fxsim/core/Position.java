package fxsim.core;

/** 一个持仓 */
public final class Position {
    public final int id;
    public final String symbol;
    public final Side side;
    public final double lots;
    /** 开仓价；期货每日盯市后会重设为结算价 */
    public double open;
    public final double margin;
    public final long openMinute;
    public final double commission;

    public double sl;     // 0 = 未设置
    public double tp;
    public double swap;

    public Position(int id, String symbol, Side side, double lots, double open,
                    double margin, long openMinute, double commission) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.lots = lots;
        this.open = open;
        this.margin = margin;
        this.openMinute = openMinute;
        this.commission = commission;
    }

    /** 平仓价：多单按买价卖出，空单按卖价买回 */
    public double closePrice(MarketEngine e) {
        PairState p = e.pair(symbol);
        return side == Side.BUY ? p.bid() : p.ask();
    }

    public double pnlNoSwap(MarketEngine e) {
        PairState p = e.pair(symbol);
        return p.ins.pnlUsd(open, closePrice(e), side.dir, lots);
    }

    public double pnl(MarketEngine e) {
        return pnlNoSwap(e) + swap - commission;
    }

    /** 当前价格距离开仓价多少点（正 = 盈利方向） */
    public double pips(MarketEngine e) {
        PairState p = e.pair(symbol);
        return (closePrice(e) - open) / p.ins.pipSize * side.dir;
    }

    /** 价格在图片上对应的水平线 */
    public double riskPrice(boolean isSl) {
        return isSl ? sl : tp;
    }
}
