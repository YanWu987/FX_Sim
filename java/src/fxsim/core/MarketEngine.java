package fxsim.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 行情引擎。
 * <p>
 * 每分钟生成一次价格：美元因子（所有品种共享）+ 品种自身噪声 + 趋势状态 + 时段波动，
 * 外加随机的经济新闻冲击。高层周期 K 线由分钟线聚合而来，保证各周期一致。
 */
public final class MarketEngine {
    public static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 19, 0, 0);
    private static final int WARMUP_MINUTES = 9000;
    /** 存档里每个周期最多保留多少根 K 线（够画图就行，控制存档体积） */
    private static final int SAVE_BARS = 500;

    public static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter FMT_CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter FMT_FULL = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /** 各小时的波动系数（0 点 = 经纪商时间午夜），伦敦/纽约重叠时段最高 */
    private static final double[] SESSION = {
            0.75, 0.70, 0.65, 0.65, 0.70, 0.75, 0.85, 1.00,
            1.25, 1.30, 1.30, 1.20, 1.15, 1.55, 1.60, 1.55,
            1.45, 1.20, 1.10, 1.05, 0.95, 0.85, 0.80, 0.78
    };

    private final LinkedHashMap<String, PairState> pairs = new LinkedHashMap<>();
    private final Rng rnd = new Rng(20261023L);
    private final List<NewsEvent> news = new ArrayList<>();
    public final ArrayDeque<String> log = new ArrayDeque<>();

    private long minute = -WARMUP_MINUTES;
    /** 1 倍速 = 1 现实秒 = BASE_SPEED 游戏分钟；gear 取值 1~5 */
    public static final double BASE_SPEED = 5.0;
    public int gear = 1;
    /** 实际速度：1 现实秒 = speed 游戏分钟 */
    public double speed = BASE_SPEED;
    public boolean paused = false;
    /** 周五 23:00 – 周日 23:00 休市 */
    public boolean weekendClose = true;
    public Runnable onNewDay;
    public int days = 0;

    private double acc = 0;
    private double usdShock = 0;
    private int regimeLeft = 120;
    private double regimeDrift = 0;
    private double regimeVol = 1.0;
    private long nextNews = -WARMUP_MINUTES + 180;
    private boolean warming = true;

    private long cachedOpenMinute = Long.MIN_VALUE;
    private LocalDateTime cachedOpen;

    // ---------------- 调试旋钮（不存档，重启归位） ----------------
    /** 全局波动倍率 */
    public double volMult = 1.0;
    /** 全局漂移（1/分钟），正数整体上涨 */
    public double driftBoost = 0.0;
    /** 点差倍率 */
    public double spreadMult = 1.0;
    /** 新闻频率倍率（>1 更频繁） */
    public double newsFreqMult = 1.0;
    /** 跳空概率倍率 */
    public double jumpMult = 1.0;

    private static final class Injection {
        final String symbol;
        final double perMin;
        final long until;

        Injection(String symbol, double perMin, long until) {
            this.symbol = symbol;
            this.perMin = perMin;
            this.until = until;
        }
    }

    private final List<Injection> injections = new ArrayList<>();

    public MarketEngine() {
        add(new Instrument("USD/CNY", 0.0001, 5, 100_000, 1.6, 5.2, -8.0, 0.000010, -0.15, 7.1820, "亚洲"));
        add(new Instrument("USD/CNH", 0.0001, 5, 100_000, 1.9, 5.0, -8.2, 0.000012, -0.18, 7.2050, "亚洲"));
        add(new Instrument("EUR/USD", 0.0001, 5, 100_000, 0.9, -3.1, 0.9, 0.000049, 1.0, 1.08520, "主要"));
        add(new Instrument("USD/JPY", 0.01, 3, 100_000, 0.7, 3.5, -6.2, 0.000049, -1.0, 151.880, "主要"));
        add(new Instrument("GBP/USD", 0.0001, 5, 100_000, 1.2, -2.2, 0.4, 0.000068, 1.1, 1.27340, "主要"));
        add(new Instrument("AUD/USD", 0.0001, 5, 100_000, 1.0, 1.2, -2.9, 0.000076, 0.9, 0.65820, "主要"));
        add(new Instrument("NZD/USD", 0.0001, 5, 100_000, 1.4, 0.6, -2.4, 0.000092, 0.85, 0.60450, "主要"));
        add(new Instrument("USD/CAD", 0.0001, 5, 100_000, 1.1, 2.1, -4.4, 0.000049, -0.9, 1.36510, "主要"));
        add(new Instrument("USD/CHF", 0.0001, 5, 100_000, 1.1, 2.8, -5.0, 0.000029, -1.0, 0.88240, "主要"));
        add(new Instrument("USD/HKD", 0.0001, 5, 100_000, 1.2, 3.2, -6.0, 0.0000030, -0.03, 7.8100, "亚洲", 7.8100, 0.0020));
        add(new Instrument("USD/MOP", 0.0001, 5, 100_000, 2.5, 3.2, -6.2, 0.0000040, -0.04, 8.0450, "亚洲", 8.0450, 0.0020));
        add(new Instrument("USD/TWD", 0.01, 3, 100_000, 2.0, 2.6, -5.4, 0.000017, -0.35, 32.400, "亚洲"));
        add(new Instrument("USD/KRW", 0.01, 2, 100_000, 2.2, 2.2, -5.0, 0.000033, -0.70, 1355.00, "亚洲"));
        add(new Instrument("USD/INR", 0.01, 2, 100_000, 3.0, 1.8, -4.6, 0.000014, -0.30, 83.600, "亚洲"));
        add(new Instrument("USD/RUB", 0.01, 4, 100_000, 12.0, 14.0, -22.0, 0.000052, -0.60, 92.5000, "新兴"));
        add(new Instrument("USD/MXN", 0.001, 4, 100_000, 8.0, 9.0, -14.0, 0.000050, -0.50, 17.2500, "新兴"));
        add(new Instrument("USD/ZAR", 0.001, 4, 100_000, 10.0, 6.5, -11.0, 0.000060, -0.45, 18.4000, "新兴"));
        add(new Instrument("USD/USN", 0.0001, 5, 100_000, 0.5, 0.1, -0.3, 0.0000040, 0.0, 1.00000, "特殊", 1.00000, 0.0050));
        add(new Instrument("XAU/USD", 0.1, 2, 100, 4.0, -8.5, 3.2, 0.000136, 0.6, 2385.40, "商品"));

        // 期货：合约乘数、最小变动、保证金率、单边手续费、涨跌停
        add(Instrument.future("RB", "螺纹钢", 1, 0, 10, 1, 0.000071, 3850, 0.10, 4, 7, "期货"));
        add(Instrument.future("M", "豆粕", 1, 0, 10, 1, 0.000060, 3120, 0.09, 3, 7, "期货"));
        add(Instrument.future("CU", "沪铜", 10, 0, 5, 1, 0.000066, 74500, 0.10, 15, 7, "期货"));
        add(Instrument.future("AU", "沪金", 0.02, 2, 1000, 2, 0.000049, 560.00, 0.09, 10, 6, "期货"));
        add(Instrument.future("SC", "原油", 0.1, 1, 1000, 1, 0.000110, 560.0, 0.12, 20, 8, "期货"));

        warmUp();
        warming = false;
    }

    private void add(Instrument ins) {
        pairs.put(ins.symbol, new PairState(ins));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------
    public PairState pair(String symbol) {
        PairState p = pairs.get(symbol);
        if (p == null) p = pairs.values().iterator().next();
        return p;
    }

    public Collection<PairState> all() {
        return pairs.values();
    }

    public List<String> symbols() {
        return new ArrayList<>(pairs.keySet());
    }

    /** 按资产类别取品种列表 */
    public List<String> symbols(Instrument.Asset asset) {
        List<String> out = new ArrayList<>();
        for (PairState ps : pairs.values()) {
            if (ps.ins.asset == asset) out.add(ps.ins.symbol);
        }
        return out;
    }

    public double pipValue(String symbol) {
        PairState p = pair(symbol);
        return p.ins.pipValueUsd(p.mid);
    }

    public long minute() {
        return minute;
    }

    public LocalDateTime time() {
        return BASE.plusMinutes(minute);
    }

    public LocalDateTime timeOf(long m) {
        return BASE.plusMinutes(m);
    }

    public int hour() {
        return (int) (Math.floorMod(minute, 1440L) / 60);
    }

    public List<NewsEvent> activeNews() {
        return news;
    }

    public String latestHeadline() {
        if (news.isEmpty()) return "暂无重要数据 · 市场由技术面主导";
        NewsEvent e = news.get(news.size() - 1);
        return e.headline(minute);
    }

    public void say(String s) {
        if (warming) return;
        log.addFirst("[" + time().format(FMT_CLOCK) + "] " + s);
        while (log.size() > 200) log.pollLast();
    }

    /** 设置倍速档位 1~5 */
    public void setGear(int g) {
        gear = Math.max(1, Math.min(5, g));
        speed = BASE_SPEED * gear;
    }

    // ------------------------------------------------------------------
    // 调试接口
    // ------------------------------------------------------------------
    /** 让某品种在接下来 minutes 分钟内走出约 pct 的涨跌幅（%），负数就是跌 */
    public void injectMove(String symbol, double pct, int minutes) {
        int m = Math.max(1, minutes);
        injections.add(new Injection(symbol, Math.log(1 + pct / 100.0) / m, minute + m));
    }

    public int activeInjections() {
        return injections.size();
    }

    public void clearInjections() {
        injections.clear();
    }

    /** 直接把某品种的现价改掉 */
    public void setPrice(String symbol, double price) {
        if (price <= 0) return;
        PairState ps = pair(symbol);
        ps.mid = price;
        ps.dayHigh = Math.max(ps.dayHigh, price);
        ps.dayLow = Math.min(ps.dayLow, price);
        say(String.format("【调试】%s 现价改为 %s", symbol, ps.ins.fmt(price)));
    }

    /** 快进 n 分钟（不走真实时间） */
    public void jumpMinutes(int n) {
        int steps = Math.max(0, Math.min(n, 60 * 24 * 60));
        for (int i = 0; i < steps; i++) stepMinute(false);
    }

    public void resetDebug() {
        volMult = 1.0;
        driftBoost = 0.0;
        spreadMult = 1.0;
        newsFreqMult = 1.0;
        jumpMult = 1.0;
        injections.clear();
    }

    // ------------------------------------------------------------------
    // 交易时段
    // ------------------------------------------------------------------
    /** 当前是否可以交易（周末休市开关打开时，周五 23:00 到周日 23:00 关闭） */
    public boolean marketOpen() {
        return !weekendClose || isOpenAt(time());
    }

    private static boolean isOpenAt(LocalDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        int h = t.getHour();
        if (d == DayOfWeek.SATURDAY) return false;
        if (d == DayOfWeek.SUNDAY) return h >= 23;
        if (d == DayOfWeek.FRIDAY) return h < 23;
        return true;
    }

    /** 下一次开盘时间（休市时用来提示） */
    public LocalDateTime nextOpen() {
        if (marketOpen()) return time();
        if (cachedOpenMinute == minute && cachedOpen != null) return cachedOpen;
        LocalDateTime t = time().withMinute(0).withSecond(0).withNano(0).plusHours(1);
        int guard = 0;
        while (!isOpenAt(t) && guard++ < 400) t = t.plusHours(1);
        cachedOpen = t;
        cachedOpenMinute = minute;
        return t;
    }

    // ------------------------------------------------------------------
    // 主循环
    // ------------------------------------------------------------------
    public void tick(double realSeconds) {
        if (paused) return;
        // 休市期间自动快进，免得周末干等
        double eff = speed * (marketOpen() ? 1.0 : 6.0);
        acc += realSeconds * eff;
        int n = (int) acc;
        if (n <= 0) return;
        acc -= n;
        n = Math.min(n, 4000);
        for (int i = 0; i < n; i++) stepMinute(false);
    }

    private void warmUp() {
        while (minute < -1) stepMinute(true);
    }

    private void stepMinute(boolean warm) {
        minute++;
        int h = (int) (Math.floorMod(minute, 1440L) / 60);
        double session = SESSION[h];
        boolean open = marketOpen();

        if (minute % 1440 == 0) {
            for (PairState ps : pairs.values()) {
                ps.prevClose = ps.mid;
                ps.dayOpen = ps.mid;
                ps.dayHigh = ps.mid;
                ps.dayLow = ps.mid;
            }
            if (!warm) {
                days++;
                // 休市日不结算隔夜利息（真实经纪商也不在周末计息）
                if (onNewDay != null && open) onNewDay.run();
            }
        }

        if (!open) return;   // 休市：只走时钟，价格、波动、新闻全部冻结

        if (--regimeLeft <= 0) roll();

        usdShock = 0.94 * usdShock + rnd.nextGaussian() * 0.000010 * regimeVol * session;

        if (minute >= nextNews) spawnNews(warm);
        news.removeIf(e -> minute > e.end);

        for (PairState ps : pairs.values()) {
            Instrument ins = ps.ins;
            double nv = 1.0;
            double spike = 1.0;
            for (NewsEvent e : news) {
                boolean core = e.core(ins.symbol);
                nv = Math.max(nv, e.volMult(minute, core));
                if (core) spike = Math.max(spike, 1.0 + 0.9 * e.strength(minute));
            }

            double ret = -ins.beta * (usdShock + regimeDrift) + driftBoost
                    + rnd.nextGaussian() * ins.vol * regimeVol * session * nv * volMult;
            // 联系汇率：往目标价拉回（港币、澳门币、次日美元）
            if (ins.peg > 0) {
                ret += ins.pegPull * Math.log(ins.peg / ps.mid);
            }
            // 调试注入的定向走势
            for (Injection inj : injections) {
                if (inj.symbol.equals(ins.symbol)) ret += inj.perMin;
            }
            // 偶发跳空
            if (rnd.nextDouble() < 0.00008 * jumpMult) {
                ret += (rnd.nextBoolean() ? 1 : -1) * (0.0006 + rnd.nextDouble() * 0.0018);
            }
            ps.mid *= Math.exp(ret);

            // 期货涨跌停：按昨结算价 ±limitPct 封板
            if (ins.limitPct > 0) {
                double up = ps.dayOpen * (1 + ins.limitPct / 100.0);
                double dn = ps.dayOpen * (1 - ins.limitPct / 100.0);
                if (ps.mid > up) ps.mid = up;
                if (ps.mid < dn) ps.mid = dn;
                ps.limitUp = ps.mid >= up - ins.pipSize * 0.999;
                ps.limitDown = ps.mid <= dn + ins.pipSize * 0.999;
            }

            double base = ins.spreadPips * spreadMult;
            double widened = base * (1.0 + 0.45 * Math.abs(rnd.nextGaussian()) + (spike - 1.0) * 1.4);
            ps.spreadPips = Math.max(base * 0.55, widened);

            ps.onTick(minute, ps.mid);
            ps.dayHigh = Math.max(ps.dayHigh, ps.mid);
            ps.dayLow = Math.min(ps.dayLow, ps.mid);
        }
        injections.removeIf(i -> minute > i.until);
    }

    private void roll() {
        regimeLeft = 120 + rnd.nextInt(360);
        double r = rnd.nextDouble();
        if (r < 0.22) {
            regimeDrift = 1.5e-6 * (rnd.nextBoolean() ? 1 : -1);
            regimeVol = 0.95;
        } else if (r < 0.40) {
            regimeDrift = 0;
            regimeVol = 1.75;
        } else {
            regimeDrift = 0;
            regimeVol = 0.85;
        }
    }

    private static final String[] CCS = {
            "USD", "USD", "USD", "CNY", "CNH", "JPY", "EUR", "GBP", "HKD", "MOP",
            "TWD", "KRW", "INR", "RUB", "AUD", "CAD", "NZD", "CHF", "MXN", "ZAR", "XAU"
    };

    private static final LinkedHashMap<String, String[]> TITLES = new LinkedHashMap<>();

    static {
        TITLES.put("USD", new String[]{"非农就业数据", "CPI 通胀数据", "FOMC 利率决议", "美联储官员讲话",
                "初请失业金人数", "零售销售数据", "ISM 制造业 PMI"});
        TITLES.put("CNY", new String[]{"人民银行 LPR 报价", "中国 CPI", "央行降准", "中国制造业 PMI", "外汇储备数据"});
        TITLES.put("CNH", new String[]{"离岸人民币流动性收紧", "央行中间价大幅调整", "离岸市场干预传闻"});
        TITLES.put("JPY", new String[]{"日本央行会议", "东京 CPI", "日银官员讲话", "日本 GDP"});
        TITLES.put("EUR", new String[]{"欧央行利率决议", "欧元区 CPI", "德国 IFO 商业景气指数", "欧元区制造业 PMI"});
        TITLES.put("GBP", new String[]{"英国央行利率决议", "英国 CPI", "英国 GDP 初值", "英国零售销售"});
        TITLES.put("HKD", new String[]{"金管局入市干预", "香港银行体系总结余变化", "港元拆息飙升"});
        TITLES.put("MOP", new String[]{"澳门金管局利率调整", "澳门旅游数据"});
        TITLES.put("TWD", new String[]{"台湾央行理监事会议", "台湾出口订单", "外资汇入汇出数据"});
        TITLES.put("KRW", new String[]{"韩国央行利率决议", "韩国 CPI", "韩国出口数据"});
        TITLES.put("INR", new String[]{"印度央行回购利率", "印度 CPI", "印度贸易帐"});
        TITLES.put("RUB", new String[]{"俄罗斯央行利率决议", "俄油出口价格上限调整", "卢布结算令变化"});
        TITLES.put("AUD", new String[]{"澳洲联储决议", "澳洲就业数据", "澳洲 CPI"});
        TITLES.put("CAD", new String[]{"加拿大央行决议", "加拿大 CPI", "加拿大就业数据"});
        TITLES.put("NZD", new String[]{"新西兰联储决议", "新西兰 CPI", "新西兰就业数据"});
        TITLES.put("CHF", new String[]{"瑞士央行决议", "瑞士 CPI", "瑞士 KOF 领先指标"});
        TITLES.put("MXN", new String[]{"墨西哥央行利率决议", "墨西哥 CPI", "墨西哥工业产出"});
        TITLES.put("ZAR", new String[]{"南非央行决议", "南非 CPI", "南非矿业产出", "南非国债评级调整"});
        TITLES.put("XAU", new String[]{"美国实际利率变动", "全球避险情绪升温", "央行购金数据"});
    }

    private void spawnNews(boolean warm) {
        String ccy = CCS[rnd.nextInt(CCS.length)];
        String[] pool = TITLES.get(ccy);
        if (pool == null) pool = TITLES.get("USD");
        String title = pool[rnd.nextInt(pool.length)];

        boolean big = rnd.nextDouble() < 0.22;
        double mag = big ? 0.0014 + rnd.nextDouble() * 0.0022 : 0.00045 + rnd.nextDouble() * 0.0009;
        double jump = (rnd.nextBoolean() ? 1 : -1) * mag;
        int dur = big ? 45 + rnd.nextInt(75) : 15 + rnd.nextInt(35);

        NewsEvent e = new NewsEvent(ccy, title, jump, minute, minute + dur, big);
        news.add(e);

        for (PairState ps : pairs.values()) {
            Instrument ins = ps.ins;
            double mult;
            if (ccy.equals("USD")) {
                mult = -ins.beta;                       // 美元走强 -> 非美货币下跌
            } else if (ins.symbol.startsWith(ccy)) {
                mult = 1.0;                             // 该货币是基准货币
            } else if (ins.symbol.endsWith(ccy)) {
                mult = -1.0;                            // 该货币是报价货币
            } else {
                mult = ins.beta * 0.18;                 // 轻微溢出
            }
            ps.mid *= Math.exp(jump * mult);
        }

        if (!warm) {
            say((big ? "【重磅】" : "【数据】") + e.headline(minute));
        }

        nextNews = minute + (int) ((big ? 360 + rnd.nextInt(600) : 150 + rnd.nextInt(330))
                / Math.max(0.05, newsFreqMult));
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------
    /** 模拟状态必须精确往返，否则读档后会越走越偏 */
    private static String d(double v) {
        return Double.toString(v);
    }

    /** K 线只用于画图，7 位小数足够，能省一半存档体积 */
    private static String f(double v) {
        return String.format(Locale.ROOT, "%.7f", v);
    }

    void write(PrintWriter p) {
        p.println("MARKET");
        p.println("minute\t" + minute);
        p.println("days\t" + days);
        p.println("speed\t" + d(speed));
        p.println("gear\t" + gear);
        p.println("weekend\t" + (weekendClose ? 1 : 0));
        p.println("rng\t" + rnd.state() + "\t" + rnd.hasCachedGauss() + "\t" + d(rnd.cachedGauss()));
        p.println("usdShock\t" + d(usdShock));
        p.println("regime\t" + regimeLeft + "\t" + d(regimeDrift) + "\t" + d(regimeVol));
        p.println("nextNews\t" + nextNews);
        p.println("news\t" + news.size());
        for (NewsEvent e : news) {
            p.println("nw\t" + e.ccy + "\t" + e.title + "\t" + d(e.jump) + "\t" + e.start + "\t" + e.end + "\t"
                    + (e.big ? 1 : 0));
        }
        for (PairState ps : pairs.values()) {
            p.println("pair\t" + ps.ins.symbol + "\t" + d(ps.mid) + "\t" + d(ps.spreadPips) + "\t"
                    + d(ps.dayOpen) + "\t" + d(ps.dayHigh) + "\t" + d(ps.dayLow) + "\t" + d(ps.prevClose));
        }
        for (PairState ps : pairs.values()) {
            for (Timeframe tf : Timeframe.values()) {
                ArrayDeque<Candle> q = ps.hist.get(tf);
                int n = q.size();
                int skip = Math.max(0, n - SAVE_BARS);
                p.println("series\t" + ps.ins.symbol + "\t" + tf.name() + "\t" + (n - skip));
                int i = 0;
                for (Candle c : q) {
                    if (i++ < skip) continue;
                    p.println(c.t + "\t" + f(c.o) + "\t" + f(c.h) + "\t" + f(c.l) + "\t" + f(c.c));
                }
                Candle cur = ps.cur.get(tf);
                if (cur != null) {
                    p.println("cur\t" + ps.ins.symbol + "\t" + tf.name() + "\t"
                            + cur.t + "\t" + f(cur.o) + "\t" + f(cur.h) + "\t" + f(cur.l) + "\t" + f(cur.c));
                }
            }
        }
        p.println("ENDMARKET");
    }

    void read(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            if (line.equals("ENDMARKET")) return;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "minute" -> minute = Long.parseLong(f[1]);
                case "days" -> days = Integer.parseInt(f[1]);
                case "speed" -> speed = Double.parseDouble(f[1]);
                case "gear" -> setGear(Integer.parseInt(f[1]));
                case "weekend" -> weekendClose = "1".equals(f[1]);
                case "rng" -> {
                    rnd.setState(Long.parseLong(f[1]));
                    rnd.setCachedGauss(Boolean.parseBoolean(f[2]), Double.parseDouble(f[3]));
                }
                case "usdShock" -> usdShock = Double.parseDouble(f[1]);
                case "regime" -> {
                    regimeLeft = Integer.parseInt(f[1]);
                    regimeDrift = Double.parseDouble(f[2]);
                    regimeVol = Double.parseDouble(f[3]);
                }
                case "nextNews" -> nextNews = Long.parseLong(f[1]);
                case "news" -> news.clear();   // 计数行：清掉构造期预热产生的事件
                case "nw" -> news.add(new NewsEvent(f[1], f[2], Double.parseDouble(f[3]),
                        Long.parseLong(f[4]), Long.parseLong(f[5]), "1".equals(f[6])));
                case "pair" -> {
                    PairState ps = pairs.get(f[1]);
                    if (ps == null) continue;
                    ps.mid = Double.parseDouble(f[2]);
                    ps.spreadPips = Double.parseDouble(f[3]);
                    ps.dayOpen = Double.parseDouble(f[4]);
                    ps.dayHigh = Double.parseDouble(f[5]);
                    ps.dayLow = Double.parseDouble(f[6]);
                    ps.prevClose = Double.parseDouble(f[7]);
                }
                case "series" -> {
                    PairState ps = pairs.get(f[1]);
                    Timeframe tf = Timeframe.valueOf(f[2]);
                    int n = Integer.parseInt(f[3]);
                    ArrayDeque<Candle> q = ps.hist.get(tf);
                    q.clear();
                    for (int i = 0; i < n; i++) {
                        String[] c = br.readLine().split("\t");
                        q.addLast(new Candle(Long.parseLong(c[0]), Double.parseDouble(c[1]),
                                Double.parseDouble(c[2]), Double.parseDouble(c[3]), Double.parseDouble(c[4])));
                    }
                }
                case "cur" -> {
                    PairState ps = pairs.get(f[1]);
                    Timeframe tf = Timeframe.valueOf(f[2]);
                    ps.cur.put(tf, new Candle(Long.parseLong(f[3]), Double.parseDouble(f[4]),
                            Double.parseDouble(f[5]), Double.parseDouble(f[6]), Double.parseDouble(f[7])));
                }
                default -> {
                    // 未知字段忽略，向前兼容
                }
            }
        }
        cachedOpenMinute = Long.MIN_VALUE;
    }
}
