package fxsim.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 模拟账户：余额、净值、保证金、持仓、挂单、成交历史、隔夜利息、强平。
 * 账户货币固定 USD。
 */
public final class Account {
    public static final double[] LEVERAGES = {1, 10, 50, 100, 500, 1000};

    public double balance = 10_000.0;
    public double leverage = 100.0;
    public double stopOutLevel = 50.0;
    public double commissionPerLot = 0.0;
    /** 这个账户做哪类资产：外汇按杠杆，期货按保证金率 + 每日盯市 */
    public Instrument.Asset asset = Instrument.Asset.FX;

    public final List<Position> positions = new ArrayList<>();
    public final List<PendingOrder> pending = new ArrayList<>();
    public final List<TradeRecord> history = new ArrayList<>();

    public int nextId = 1;
    public String lastError = "";
    public boolean marginCall = false;

    private final MarketEngine eng;

    public Account(MarketEngine eng) {
        this.eng = eng;
    }

    public void reset() {
        balance = 10_000.0;
        positions.clear();
        pending.clear();
        history.clear();
        nextId = 1;
        lastError = "";
        marginCall = false;
        eng.say("账户已重置：初始资金 10,000 USD");
    }

    public void setLeverage(double lev) {
        double v = Math.max(1, Math.min(1000, lev));
        if (Math.abs(v - leverage) < 1e-9) return;
        leverage = v;
        eng.say(String.format("杠杆调整为 1:%d（只影响之后新开的仓位）", (int) v));
    }

    // ------------------------------------------------------------------
    // 账户指标
    // ------------------------------------------------------------------
    public double floating() {
        double s = 0;
        for (Position p : positions) s += p.pnlNoSwap(eng) + p.swap - p.commission;
        return s;
    }

    public double equity() {
        return balance + floating();
    }

    public double marginUsed() {
        double m = 0;
        for (Position p : positions) m += p.margin;
        return m;
    }

    public double freeMargin() {
        return equity() - marginUsed();
    }

    public double marginLevel() {
        double mu = marginUsed();
        if (mu <= 0) return 0;
        return equity() / mu * 100.0;
    }

    public double marginRequired(String symbol, double lots) {
        Instrument ins = eng.pair(symbol).ins;
        if (asset == Instrument.Asset.FUT) {
            return ins.notionalUsd(lots, eng.pair(symbol).mid) * ins.marginRate;
        }
        return ins.notionalUsd(lots, eng.pair(symbol).mid) / leverage;
    }

    // ------------------------------------------------------------------
    // 下单
    // ------------------------------------------------------------------
    public boolean canOpen(String symbol, double lots) {
        if (!eng.marketOpen()) {
            lastError = "市场休市，无法交易";
            return false;
        }
        if (lots <= 0) {
            lastError = "手数必须大于 0";
            return false;
        }
        PairState ps = eng.pair(symbol);
        if (ps.limitUp) {
            lastError = "封涨停板，买不进去";
            return false;
        }
        if (ps.limitDown) {
            lastError = "封跌停板，卖不出去";
            return false;
        }
        double need = marginRequired(symbol, lots);
        if (need > freeMargin()) {
            lastError = String.format("保证金不足：需要 %.2f，可用 %.2f", need, freeMargin());
            return false;
        }
        return true;
    }

    public boolean openMarket(String symbol, Side side, double lots) {
        return openMarket(symbol, side, lots, 0, 0);
    }

    public boolean openMarket(String symbol, Side side, double lots, double sl, double tp) {
        if (!canOpen(symbol, lots)) return false;
        PairState ps = eng.pair(symbol);
        Instrument ins = ps.ins;
        double px = side == Side.BUY ? ps.ask() : ps.bid();
        double margin = marginRequired(symbol, lots);
        double comm = asset == Instrument.Asset.FUT ? ins.feePerLot * lots : commissionPerLot * lots;

        Position p = new Position(nextId++, symbol, side, lots, px, margin, eng.minute(), comm);
        p.sl = sl;
        p.tp = tp;
        positions.add(p);
        balance -= comm;
        history.add(new TradeRecord(eng.minute(), symbol, "开仓 " + side.en, lots, px, -comm, balance));
        eng.say(String.format("%s %s %.2f 手 @ %s", symbol, side.cn, lots, ins.fmt(px)));
        lastError = "";
        return true;
    }

    public boolean placePending(String symbol, Side side, double lots, PendingOrder.Type type, double trigger) {
        return placePending(symbol, side, lots, type, trigger, 0, 0);
    }

    public boolean placePending(String symbol, Side side, double lots, PendingOrder.Type type,
                                double trigger, double sl, double tp) {
        if (!eng.marketOpen()) {
            lastError = "市场休市，无法挂单";
            return false;
        }
        if (!canOpen(symbol, lots)) {
            // 挂单不占用保证金，但仍要求账户状态健康
            if (freeMargin() <= 0) return false;
        }
        pending.add(new PendingOrder(nextId++, symbol, side, lots, type, trigger, eng.minute(), sl, tp));
        eng.say(String.format("%s %s %s %.2f 手 @ %s", symbol, type.cn + "单", side.cn, lots,
                eng.pair(symbol).ins.fmt(trigger)));
        lastError = "";
        return true;
    }

    public void cancel(PendingOrder o) {
        pending.remove(o);
        eng.say("已取消挂单 #" + o.id);
    }

    public void close(Position p, String reason) {
        if (!positions.remove(p)) return;
        double px = p.closePrice(eng);
        double fee = asset == Instrument.Asset.FUT ? eng.pair(p.symbol).ins.feePerLot * p.lots : 0;
        double swap = asset == Instrument.Asset.FUT ? 0 : p.swap;
        double pnl = p.pnlNoSwap(eng) + swap - p.commission - fee;
        balance += pnl;
        history.add(new TradeRecord(eng.minute(), p.symbol,
                "平仓 " + p.side.en + (reason == null ? "" : " · " + reason), p.lots, px, pnl, balance));
        eng.say(String.format("%s #%d 平仓 %s 盈亏 %+.2f", p.symbol, p.id, p.side.en, pnl));
    }

    public void closeAll(String symbol) {
        List<Position> copy = new ArrayList<>(positions);
        for (Position p : copy) {
            if (symbol == null || p.symbol.equals(symbol)) close(p, "手动");
        }
    }

    /** 校验止损止盈是否合法（现实规则：不能贴着现价，否则会被立刻触发） */
    public String validateStops(Position p, double sl, double tp) {
        PairState ps = eng.pair(p.symbol);
        Instrument ins = ps.ins;
        double bid = ps.bid();
        double ask = ps.ask();
        double min = ins.pipSize * 2;
        if (sl > 0) {
            if (p.side == Side.BUY && sl > bid - min) {
                return "多单止损必须低于现价 " + ins.fmt(bid) + " 至少 2 点";
            }
            if (p.side == Side.SELL && sl < ask + min) {
                return "空单止损必须高于现价 " + ins.fmt(ask) + " 至少 2 点";
            }
        }
        if (tp > 0) {
            if (p.side == Side.BUY && tp < bid + min) {
                return "多单止盈必须高于现价 " + ins.fmt(bid) + " 至少 2 点";
            }
            if (p.side == Side.SELL && tp > ask - min) {
                return "空单止盈必须低于现价 " + ins.fmt(ask) + " 至少 2 点";
            }
        }
        return null;
    }

    public boolean modify(Position p, double sl, double tp) {
        String err = validateStops(p, sl, tp);
        if (err != null) {
            lastError = err;
            return false;
        }
        p.sl = sl;
        p.tp = tp;
        eng.say(String.format("#%d 止损止盈已更新：SL %s / TP %s", p.id,
                sl <= 0 ? "无" : eng.pair(p.symbol).ins.fmt(sl),
                tp <= 0 ? "无" : eng.pair(p.symbol).ins.fmt(tp)));
        lastError = "";
        return true;
    }

    // ------------------------------------------------------------------
    // 每帧更新
    // ------------------------------------------------------------------
    public void update() {
        if (eng.paused || !eng.marketOpen()) return;
        checkPending();
        checkStops();
        checkStopOut();
        marginCall = marginLevel() > 0 && marginLevel() < 120;
    }

    private void checkPending() {
        List<PendingOrder> copy = new ArrayList<>(pending);
        for (PendingOrder o : copy) {
            if (!o.hit(eng)) continue;
            if (canOpen(o.symbol, o.lots)) {
                openMarket(o.symbol, o.side, o.lots, o.sl, o.tp);
                pending.remove(o);
            }
        }
    }

    private void checkStops() {
        List<Position> copy = new ArrayList<>(positions);
        for (Position p : copy) {
            double bid = eng.pair(p.symbol).bid();
            double ask = eng.pair(p.symbol).ask();
            if (p.side == Side.BUY) {
                if (p.sl > 0 && bid <= p.sl) close(p, "止损");
                else if (p.tp > 0 && bid >= p.tp) close(p, "止盈");
            } else {
                if (p.sl > 0 && ask >= p.sl) close(p, "止损");
                else if (p.tp > 0 && ask <= p.tp) close(p, "止盈");
            }
        }
    }

    private void checkStopOut() {
        int guard = 0;
        while (!positions.isEmpty() && marginLevel() < stopOutLevel && guard++ < 50) {
            Position worst = null;
            double worstPnl = Double.MAX_VALUE;
            for (Position p : positions) {
                double v = p.pnlNoSwap(eng);
                if (v < worstPnl) {
                    worstPnl = v;
                    worst = p;
                }
            }
            if (worst == null) break;
            close(worst, "强制平仓");
            eng.say("⚠ 保证金水平低于 " + (int) stopOutLevel + "%，已强制平仓 #" + worst.id);
        }
    }

    /** 服务器时间跨天：外汇结算隔夜利息（周三三倍），期货做每日盯市 */
    public void onNewDay() {
        if (asset == Instrument.Asset.FUT) {
            settleDaily();
            return;
        }
        boolean triple = eng.time().getDayOfWeek() == DayOfWeek.WEDNESDAY;
        double mult = triple ? 3 : 1;
        double total = 0;
        for (Position p : positions) {
            Instrument ins = eng.pair(p.symbol).ins;
            double per = p.side == Side.BUY ? ins.swapLong : ins.swapShort;
            double sw = per * p.lots * mult;
            p.swap += sw;
            total += sw;
        }
        if (Math.abs(total) > 0.005) {
            eng.say(String.format("隔夜利息结算%s：%+.2f USD", triple ? "（周三三倍）" : "", total));
        }
    }

    /** 期货逐日盯市：浮盈浮亏划进余额，开仓价重设为结算价 */
    private void settleDaily() {
        double total = 0;
        for (Position p : positions) {
            total += p.pnlNoSwap(eng);
            // 按平仓价结算，结算后浮动盈亏归零
            p.open = p.closePrice(eng);
        }
        balance += total;
        if (!positions.isEmpty()) {
            eng.say(String.format("【盯市】结算 %+.2f，余额 %.2f", total, balance));
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------
    private static String d(double v) {
        return Double.toString(v);
    }

    void write(PrintWriter p) {
        p.println("ACCOUNT");
        p.println("asset\t" + asset.name());
        p.println("balance\t" + d(balance));
        p.println("leverage\t" + d(leverage));
        p.println("stopOut\t" + d(stopOutLevel));
        p.println("commission\t" + d(commissionPerLot));
        p.println("nextId\t" + nextId);
        p.println("positions\t" + positions.size());
        for (Position x : positions) {
            p.println("pos\t" + x.id + "\t" + x.symbol + "\t" + x.side.dir + "\t" + d(x.lots) + "\t"
                    + d(x.open) + "\t" + d(x.margin) + "\t" + x.openMinute + "\t" + d(x.commission) + "\t"
                    + d(x.sl) + "\t" + d(x.tp) + "\t" + d(x.swap));
        }
        p.println("pending\t" + pending.size());
        for (PendingOrder o : pending) {
            p.println("ord\t" + o.id + "\t" + o.symbol + "\t" + o.side.dir + "\t" + d(o.lots) + "\t"
                    + o.type.name() + "\t" + d(o.trigger) + "\t" + o.created + "\t" + d(o.sl) + "\t" + d(o.tp));
        }
        p.println("history\t" + history.size());
        for (TradeRecord r : history) {
            p.println("rec\t" + r.minute + "\t" + r.symbol + "\t" + r.action + "\t" + d(r.lots) + "\t"
                    + d(r.price) + "\t" + d(r.pnl) + "\t" + d(r.balance));
        }
        p.println("END");
    }

    void read(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            if (line.equals("END")) return;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "asset" -> asset = Instrument.Asset.valueOf(f[1]);
                case "balance" -> balance = Double.parseDouble(f[1]);
                case "leverage" -> leverage = Double.parseDouble(f[1]);
                case "stopOut" -> stopOutLevel = Double.parseDouble(f[1]);
                case "commission" -> commissionPerLot = Double.parseDouble(f[1]);
                case "nextId" -> nextId = Integer.parseInt(f[1]);
                case "pos" -> {
                    Position x = new Position(Integer.parseInt(f[1]), f[2], Side.of(Integer.parseInt(f[3])),
                            Double.parseDouble(f[4]), Double.parseDouble(f[5]), Double.parseDouble(f[6]),
                            Long.parseLong(f[7]), Double.parseDouble(f[8]));
                    x.sl = Double.parseDouble(f[9]);
                    x.tp = Double.parseDouble(f[10]);
                    x.swap = Double.parseDouble(f[11]);
                    positions.add(x);
                }
                case "ord" -> pending.add(new PendingOrder(Integer.parseInt(f[1]), f[2],
                        Side.of(Integer.parseInt(f[3])), Double.parseDouble(f[4]),
                        PendingOrder.Type.valueOf(f[5]), Double.parseDouble(f[6]),
                        Long.parseLong(f[7]), Double.parseDouble(f[8]), Double.parseDouble(f[9])));
                case "rec" -> history.add(new TradeRecord(Long.parseLong(f[1]), f[2], f[3],
                        Double.parseDouble(f[4]), Double.parseDouble(f[5]),
                        Double.parseDouble(f[6]), Double.parseDouble(f[7])));
                default -> {
                    // positions / pending / history 的计数行，忽略
                }
            }
        }
    }
}
