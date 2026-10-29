package fxsim.core;

/**
 * 一个交易品种的静态定义。
 * 账户货币固定为 USD；期货按"合约乘数 + 保证金率"计算，外汇按"名义价值 / 杠杆"计算。
 */
public final class Instrument {
    /** 资产类别 */
    public enum Asset {FX, FUT}

    public final Asset asset;
    public final String symbol;
    public final String base;
    public final String quote;

    /** 1 个点的价格步长：EUR/USD 0.0001，USD/JPY 0.01，螺纹 1；期货里就是最小变动价位 */
    public final double pipSize;
    /** 价格显示小数位 */
    public final int digits;
    /** 1 手的合约量：外汇是基础货币单位，期货是合约乘数（如螺纹 10 吨/手） */
    public final double contractSize;

    /** 常态点差（点） */
    public final double spreadPips;
    /** 每手每日隔夜利息（USD），做多/做空；期货不使用 */
    public final double swapLong;
    public final double swapShort;

    /** 每分钟对数波动率（约 0.0002 = 单日 ~0.76%） */
    public final double vol;
    /** 对美元因子的敏感度：EUR/USD = +1，USD/JPY = -1，期货 = 0 */
    public final double beta;
    /** 起始价格 */
    public final double startPrice;
    /** 分组："主要" / "亚洲" / "新兴" / "商品" / "期货" */
    public final String group;

    /** 联系汇率目标价，0 = 自由浮动 */
    public final double peg;
    /** 回归目标价的强度（每分钟），0 = 不回归 */
    public final double pegPull;

    /** 期货保证金率（如 0.10 = 10%），外汇为 0 */
    public final double marginRate;
    /** 期货单边手续费（每手，账户货币），外汇为 0 */
    public final double feePerLot;
    /** 期货涨跌停幅度（%，相对昨结算），0 = 不限 */
    public final double limitPct;

    public Instrument(String symbol, double pipSize, int digits, double contractSize,
                      double spreadPips, double swapLong, double swapShort,
                      double vol, double beta, double startPrice, String group) {
        this(Asset.FX, symbol, pipSize, digits, contractSize, spreadPips, swapLong, swapShort,
                vol, beta, startPrice, group, 0, 0, 0, 0, 0);
    }

    public Instrument(String symbol, double pipSize, int digits, double contractSize,
                      double spreadPips, double swapLong, double swapShort,
                      double vol, double beta, double startPrice, String group,
                      double peg, double pegPull) {
        this(Asset.FX, symbol, pipSize, digits, contractSize, spreadPips, swapLong, swapShort,
                vol, beta, startPrice, group, peg, pegPull, 0, 0, 0);
    }

    private Instrument(Asset asset, String symbol, double pipSize, int digits, double contractSize,
                       double spreadPips, double swapLong, double swapShort,
                       double vol, double beta, double startPrice, String group,
                       double peg, double pegPull, double marginRate, double feePerLot, double limitPct) {
        this.asset = asset;
        this.symbol = symbol;
        int i = symbol.indexOf('/');
        this.base = i > 0 ? symbol.substring(0, i) : symbol;
        this.quote = i > 0 ? symbol.substring(i + 1) : "USD";
        this.pipSize = pipSize;
        this.digits = digits;
        this.contractSize = contractSize;
        this.spreadPips = spreadPips;
        this.swapLong = swapLong;
        this.swapShort = swapShort;
        this.vol = vol;
        this.beta = beta;
        this.startPrice = startPrice;
        this.group = group;
        this.peg = peg;
        this.pegPull = pegPull;
        this.marginRate = marginRate;
        this.feePerLot = feePerLot;
        this.limitPct = limitPct;
    }

    /** 期货合约：symbol 如 "RB"，乘数 10 吨/手，最小变动 1，保证金率 10% */
    public static Instrument future(String symbol, String name, double tick, int digits,
                                    double multiplier, double spreadTicks, double vol,
                                    double startPrice, double marginRate, double feePerLot,
                                    double limitPct, String group) {
        return new Instrument(Asset.FUT, symbol, tick, digits, multiplier, spreadTicks, 0, 0,
                vol, 0, startPrice, group, 0, 0, marginRate, feePerLot, limitPct);
    }

    /** 报价货币 -> USD 的汇率；期货直接用账户货币计价 */
    public double quoteToUsd(double price) {
        if (asset == Asset.FUT) return 1.0;
        return quote.equals("USD") ? 1.0 : 1.0 / price;
    }

    /** 每手每 1 点的价值（USD） */
    public double pipValueUsd(double price) {
        return pipSize * contractSize * quoteToUsd(price);
    }

    /** 名义价值（USD） */
    public double notionalUsd(double lots, double price) {
        double q = lots * contractSize;
        return base.equals("USD") ? q : q * price;
    }

    /** 开平仓价差带来的盈亏（USD），不含隔夜利息/手续费 */
    public double pnlUsd(double open, double close, int dir, double lots) {
        return (close - open) * dir * lots * contractSize * quoteToUsd(close);
    }

    public double round(double p) {
        double f = Math.pow(10, digits);
        return Math.round(p * f) / f;
    }

    public String fmt(double p) {
        return String.format("%." + digits + "f", p);
    }
}
