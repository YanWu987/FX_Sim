package fxsim.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * 玩家本人的状态：生命值、饱食度、口渴度、困意、负债。
 * 全部按"游戏内分钟"结算——加速时日子过得快，饿得快、困得也快。
 */
public final class Player {
    public static final double MAX_LOAN = 20_000;
    public static final double DAILY_RATE = 0.0015;   // 日息 0.15%
    public static final double MAX = 100;

    /** 商店里能买的东西：drowsy 为负数表示提神；type 1=咖啡 2=能量饮料 0=其他 */
    public record Item(String name, double cost, double satiety, double thirst, double drowsy, int type) {
    }

    public static final Item[] SHOP = {
            new Item("面包", 8, 22, 0, 0, 0),
            new Item("正餐", 22, 55, 6, 0, 0),
            new Item("矿泉水", 3, 0, 28, 0, 0),
            new Item("桶装水", 8, 0, 65, 0, 0),
            new Item("咖啡", 6, 4, 20, -50, 1),
            new Item("能量饮料", 9, 0, 10, -100, 2),
    };

    public double hp = 100;
    public double satiety = 85;
    public double thirst = 85;
    public double drowsiness = 20;
    public double debt = 0;

    /** 距离上次睡觉累计喝了几杯咖啡 / 几罐能量饮料 */
    public int coffeeCount = 0;
    public int energyCount = 0;

    /** 待跳过的时间（住院用），由 settleSkips 消费 */
    private int skipMinutes = 0;
    /** 睡觉期间代谢放慢：饱食/口渴只按 25% 消耗 */
    private boolean sleeping = false;
    private long lastMinute = Long.MIN_VALUE;
    private long lastDay = Long.MIN_VALUE;

    private final MarketEngine eng;

    public Player(MarketEngine eng) {
        this.eng = eng;
        sync();
    }

    public void sync() {
        if (lastMinute == Long.MIN_VALUE) {
            lastMinute = eng.minute();
            lastDay = Math.floorDiv(lastMinute, 1440L);
        }
    }

    public void reset() {
        hp = 100;
        satiety = 85;
        thirst = 85;
        drowsiness = 20;
        debt = 0;
        coffeeCount = 0;
        energyCount = 0;
        skipMinutes = 0;
        lastMinute = eng.minute();
        lastDay = Math.floorDiv(lastMinute, 1440L);
    }

    // ------------------------------------------------------------------
    // 每分钟结算
    // ------------------------------------------------------------------
    public void update(Account acc) {
        sync();
        long now = eng.minute();
        long dt = now - lastMinute;
        if (dt > 0) {
            lastMinute = now;
            // 单帧最多结算 4 小时，避免一次巨大跳变直接把人饿死
            dt = Math.min(dt, 240);

            // 饱食度 2 天见底，口渴度 1.2 天见底，困意 2.5 天到头；睡觉时只按 25% 消耗
            double rate = sleeping ? 0.25 : 1.0;
            satiety = Math.max(0, satiety - 100.0 / (2 * 1440.0) * dt * rate);
            thirst = Math.max(0, thirst - 100.0 / (1.2 * 1440.0) * dt * rate);
            if (!sleeping) {
                drowsiness = Math.min(MAX, drowsiness + 100.0 / (2.5 * 1440.0) * dt);
            }

            double drain = 0;
            if (thirst <= 0) drain += 100.0 / (5 * 1440.0);   // 不喝水 5 天扣光
            if (satiety <= 0) drain += 100.0 / (10 * 1440.0); // 不吃饭 10 天扣光
            if (drowsiness >= MAX) drain += 100.0 / (4 * 1440.0); // 困到头 4 天扣光
            if (drain > 0) {
                hp = Math.max(0, hp - drain * dt);
            } else if (satiety >= 50 && thirst >= 50 && drowsiness < 80) {
                hp = Math.min(MAX, hp + 100.0 / (7 * 1440.0) * dt);
            }

            // 每天结算贷款利息
            long day = Math.floorDiv(now, 1440L);
            if (day > lastDay) {
                long days = day - lastDay;
                lastDay = day;
                if (debt > 0) {
                    double interest = debt * DAILY_RATE * days;
                    acc.balance -= interest;
                    if (interest > 0.005) {
                        eng.say(String.format("贷款利息 -%.2f（负债 %s，日息 %.2f%%）",
                                interest, money(debt), DAILY_RATE * 100));
                    }
                }
            }
        }
        if (hp <= 0) hospital(acc);
    }

    /** 住院：扣医药费、醒来、并且跳过两天 */
    private void hospital(Account acc) {
        double fee = Math.max(500, Math.max(0, acc.balance) * 0.10);
        acc.balance -= fee;
        hp = 45;
        satiety = Math.max(satiety, 60);
        thirst = Math.max(thirst, 60);
        drowsiness = 0;
        coffeeCount = 0;
        energyCount = 0;
        skipMinutes += 2 * 1440;
        eng.say(String.format("⚠ 倒下了，被送进医院：医药费 %.2f，住院两天", fee));
    }

    // ------------------------------------------------------------------
    // 睡觉 / 时间跳过
    // ------------------------------------------------------------------
    /** 睡一觉：困意归零、重置过量计数，跳过一天；期间行情照走、仓位照管 */
    public void sleep(Account acc) {
        drowsiness = 0;
        coffeeCount = 0;
        energyCount = 0;
        eng.say("睡了整整一天（这天的行情你完全没管）");
        sleeping = true;
        advance(acc, 1440);
        sleeping = false;
        drowsiness = 0;
    }

    /** 把住院等待的时间一次性走完 */
    public void settleSkips(Account acc) {
        if (skipMinutes <= 0) return;
        int n = skipMinutes;
        skipMinutes = 0;
        advance(acc, n);
    }

    /** 快进 n 分钟：行情、账户、玩家状态一起推进 */
    private void advance(Account acc, int minutes) {
        int left = minutes;
        while (left-- > 0) {
            eng.jumpMinutes(1);
            acc.update();
            update(acc);
            if (skipMinutes > 0) {
                left += skipMinutes;
                skipMinutes = 0;
            }
            if (hp <= 0) break;
        }
    }

    // ------------------------------------------------------------------
    // 商店
    // ------------------------------------------------------------------
    /** 购买并立即食用，返回错误信息（null = 成功） */
    public String buy(Item it, Account acc) {
        if (acc.balance < it.cost) return "余额不足，需要 " + money(it.cost);
        acc.balance -= it.cost;
        if (it.satiety() > 0) satiety = Math.min(MAX, satiety + it.satiety());
        if (it.thirst() > 0) thirst = Math.min(MAX, thirst + it.thirst());
        if (it.drowsy() != 0) drowsiness = Math.max(0, Math.min(MAX, drowsiness + it.drowsy()));

        if (it.type() == 1) {
            coffeeCount++;
            if (coffeeCount >= 5) {
                hp = 0;
                eng.say("⚠ 咖啡因过量：第 5 杯下肚，直接倒下");
            }
        } else if (it.type() == 2) {
            energyCount++;
            if (energyCount >= 3) {
                hp = 0;
                eng.say("⚠ 能量饮料过量：第 3 罐下肚，直接倒下");
            }
        }

        eng.say(String.format("吃喝：%s  饱食 +%.0f  口渴 +%.0f  困意 %+.0f  花费 %.2f",
                it.name(), it.satiety(), it.thirst(), it.drowsy(), it.cost));
        return null;
    }

    // ------------------------------------------------------------------
    // 贷款
    // ------------------------------------------------------------------
    public String borrow(double amount, Account acc) {
        if (debt + amount > MAX_LOAN) {
            return String.format("超出额度：最多还能借 %s", money(MAX_LOAN - debt));
        }
        debt += amount;
        acc.balance += amount;
        eng.say(String.format("借款 %s，当前负债 %s，日息 %.2f%%", money(amount), money(debt), DAILY_RATE * 100));
        return null;
    }

    public String repay(double amount, Account acc) {
        double amt = Math.min(amount, debt);
        if (amt <= 0) return "当前没有负债";
        if (acc.balance < amt) return "余额不足，无法还款 " + money(amt);
        acc.balance -= amt;
        debt -= amt;
        if (debt < 0.005) debt = 0;
        eng.say(String.format("还款 %s，剩余负债 %s", money(amt), money(debt)));
        return null;
    }

    public double dailyInterest() {
        return debt * DAILY_RATE;
    }

    public static String money(double v) {
        return String.format("%,.2f", v);
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------
    void write(PrintWriter p) {
        p.println("PLAYER");
        p.println("hp\t" + hp);
        p.println("satiety\t" + satiety);
        p.println("thirst\t" + thirst);
        p.println("drowsy\t" + drowsiness);
        p.println("debt\t" + debt);
        p.println("coffee\t" + coffeeCount);
        p.println("energy\t" + energyCount);
        p.println("lastMinute\t" + lastMinute);
        p.println("lastDay\t" + lastDay);
        p.println("ENDP");
    }

    void read(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            if (line.equals("ENDP")) return;
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "hp" -> hp = Double.parseDouble(f[1]);
                case "satiety" -> satiety = Double.parseDouble(f[1]);
                case "thirst" -> thirst = Double.parseDouble(f[1]);
                case "drowsy" -> drowsiness = Double.parseDouble(f[1]);
                case "debt" -> debt = Double.parseDouble(f[1]);
                case "coffee" -> coffeeCount = Integer.parseInt(f[1]);
                case "energy" -> energyCount = Integer.parseInt(f[1]);
                case "lastMinute" -> lastMinute = Long.parseLong(f[1]);
                case "lastDay" -> lastDay = Long.parseLong(f[1]);
                default -> {
                }
            }
        }
    }
}
