package fxsim.core;

/** 方向 */
public enum Side {
    BUY(1, "买入", "BUY"),
    SELL(-1, "卖出", "SELL");

    public final int dir;
    public final String cn;
    public final String en;

    Side(int dir, String cn, String en) {
        this.dir = dir;
        this.cn = cn;
        this.en = en;
    }

    public static Side of(int dir) {
        return dir >= 0 ? BUY : SELL;
    }
}
