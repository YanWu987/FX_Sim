package fxsim.core;

/** 成交流水 */
public final class TradeRecord {
    public final long minute;
    public final String symbol;
    public final String action;
    public final double lots;
    public final double price;
    public final double pnl;
    public final double balance;

    public TradeRecord(long minute, String symbol, String action, double lots, double price, double pnl, double balance) {
        this.minute = minute;
        this.symbol = symbol;
        this.action = action;
        this.lots = lots;
        this.price = price;
        this.pnl = pnl;
        this.balance = balance;
    }
}
