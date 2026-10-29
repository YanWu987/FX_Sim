package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.Player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** 调试台：实时改参数、注入走势、改账户和玩家数值。Ctrl+D 打开。 */
public final class DebugDialog extends JDialog {
    private final MarketEngine engine;
    private final Account account;
    private final Player player;

    private final JComboBox<String> symBox;
    private final JTextField movePct;
    private final JTextField moveMin;
    private final JTextField priceField;
    private final JLabel status = UI.label("", 10, Theme.I.muted, false);
    private final Chip[] gearChips = new Chip[5];
    private final Timer timer;
    private boolean closed;

    public DebugDialog(Frame owner, MarketEngine engine, Account account, Player player) {
        super(owner, "调试台", false);
        this.engine = engine;
        this.account = account;
        this.player = player;
        Theme t = Theme.I;
        setSize(480, 860);
        setLocationRelativeTo(owner);
        setResizable(false);

        JPanel root = new JPanel();
        root.setBackground(t.panel);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel head = UI.label("调试台", 15, t.text, true);
        head.setAlignmentX(LEFT_ALIGNMENT);
        root.add(head);
        JLabel sub = UI.label("改动立即生效，不写入存档；重启后参数复位", 10, t.muted, false);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        root.add(sub);
        root.add(gap(10));

        // ---------------- 行情 ----------------
        root.add(sectionTitle("行情参数"));
        JTextField vol = field(String.valueOf(engine.volMult));
        root.add(row("波动倍率", vol, "应用", () -> engine.volMult = num(vol, 1.0)));
        JTextField drift = field(String.format("%.2f", engine.driftBoost * 1440 * 100));
        root.add(row("整体漂移 %/天", drift, "应用", () -> engine.driftBoost = num(drift, 0) / 100.0 / 1440.0));
        JTextField spread = field(String.valueOf(engine.spreadMult));
        root.add(row("点差倍率", spread, "应用", () -> engine.spreadMult = num(spread, 1.0)));
        JTextField news = field(String.valueOf(engine.newsFreqMult));
        root.add(row("新闻频率倍率", news, "应用", () -> engine.newsFreqMult = num(news, 1.0)));
        JTextField jump = field(String.valueOf(engine.jumpMult));
        root.add(row("跳空概率倍率", jump, "应用", () -> engine.jumpMult = num(jump, 1.0)));
        root.add(gap(4));
        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(LEFT_ALIGNMENT);
        line1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        line1.add(new FlatButton("恢复默认", () -> Theme.I.panelAlt, () -> Theme.I.muted, () -> {
            engine.resetDebug();
            vol.setText("1.0");
            drift.setText("0.00");
            spread.setText("1.0");
            news.setText("1.0");
            jump.setText("1.0");
            status.setText("已恢复行情默认参数");
        }));
        root.add(line1);
        root.add(gap(12));

        // ---------------- 盘面注入 ----------------
        root.add(sectionTitle("盘面干预"));
        symBox = new JComboBox<>(engine.symbols().toArray(new String[0]));
        symBox.setBackground(t.panelAlt);
        symBox.setForeground(t.text);
        symBox.setFont(t.font(11, false));
        symBox.setPreferredSize(new Dimension(120, 26));

        JPanel injectRow = new JPanel(new BorderLayout(6, 0));
        injectRow.setOpaque(false);
        injectRow.setAlignmentX(LEFT_ALIGNMENT);
        injectRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        injectRow.add(symBox, BorderLayout.WEST);
        movePct = field("1.0", 52);
        moveMin = field("60", 52);
        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        mid.setOpaque(false);
        mid.add(UI.label("幅度%", 10, t.muted, false));
        mid.add(movePct);
        mid.add(UI.label("分钟", 10, t.muted, false));
        mid.add(moveMin);
        injectRow.add(mid, BorderLayout.CENTER);
        FlatButton go = new FlatButton("注入", () -> Theme.I.accent, () -> Color.WHITE, this::inject);
        go.setPreferredSize(new Dimension(56, 26));
        injectRow.add(go, BorderLayout.EAST);
        root.add(injectRow);

        JPanel priceRow = new JPanel(new BorderLayout(6, 0));
        priceRow.setOpaque(false);
        priceRow.setAlignmentX(LEFT_ALIGNMENT);
        priceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        priceField = field("", 92);
        JPanel pl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        pl.setOpaque(false);
        pl.add(UI.label("把现价设为", 10, t.muted, false));
        pl.add(priceField);
        priceRow.add(pl, BorderLayout.CENTER);
        FlatButton setBtn = new FlatButton("设价", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> {
            engine.setPrice((String) symBox.getSelectedItem(), num(priceField, 0));
            status.setText("已改价");
        });
        setBtn.setPreferredSize(new Dimension(56, 26));
        priceRow.add(setBtn, BorderLayout.EAST);
        root.add(priceRow);

        JPanel clearRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        clearRow.setOpaque(false);
        clearRow.setAlignmentX(LEFT_ALIGNMENT);
        clearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        clearRow.add(new FlatButton("清除所有注入", () -> Theme.I.panelAlt, () -> Theme.I.muted, () -> {
            engine.clearInjections();
            status.setText("已清除注入");
        }));
        root.add(clearRow);
        status.setAlignmentX(LEFT_ALIGNMENT);
        root.add(status);
        root.add(gap(12));

        // ---------------- 账户 ----------------
        root.add(sectionTitle("账户"));
        JTextField bal = field(String.format("%.2f", account.balance));
        root.add(row("余额", bal, "设置", () -> account.balance = num(bal, account.balance)));
        JTextField lev = field(String.valueOf((int) account.leverage));
        root.add(row("杠杆 1:", lev, "设置", () -> account.setLeverage(num(lev, account.leverage))));
        JTextField stop = field(String.valueOf((int) account.stopOutLevel));
        root.add(row("强平线 %", stop, "设置", () -> account.stopOutLevel = num(stop, account.stopOutLevel)));
        root.add(gap(12));

        // ---------------- 玩家 ----------------
        root.add(sectionTitle("玩家"));
        JTextField hp = field(String.format("%.0f", player.hp));
        root.add(row("生命值", hp, "设置", () -> player.hp = num(hp, player.hp)));
        JTextField food = field(String.format("%.0f", player.satiety));
        root.add(row("饱食度", food, "设置", () -> player.satiety = num(food, player.satiety)));
        JTextField water = field(String.format("%.0f", player.thirst));
        root.add(row("口渴度", water, "设置", () -> player.thirst = num(water, player.thirst)));
        JTextField debt = field(String.format("%.0f", player.debt));
        root.add(row("负债", debt, "设置", () -> player.debt = num(debt, player.debt)));
        root.add(gap(12));

        // ---------------- 时间 ----------------
        root.add(sectionTitle("时间"));
        JPanel gearRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        gearRow.setOpaque(false);
        gearRow.setAlignmentX(LEFT_ALIGNMENT);
        gearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        for (int i = 0; i < 5; i++) {
            int g = i + 1;
            gearChips[i] = new Chip(g + "×", engine.gear == g, () -> {
                engine.setGear(g);
                syncGear();
            });
            gearRow.add(gearChips[i]);
        }
        root.add(gearRow);

        JPanel jumpRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        jumpRow.setOpaque(false);
        jumpRow.setAlignmentX(LEFT_ALIGNMENT);
        jumpRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        jumpRow.add(new FlatButton("+1 小时", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> engine.jumpMinutes(60)));
        jumpRow.add(new FlatButton("+1 天", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> engine.jumpMinutes(1440)));
        jumpRow.add(new FlatButton("+7 天", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> engine.jumpMinutes(10080)));
        root.add(jumpRow);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.setOpaque(false);
        bottom.setAlignmentX(LEFT_ALIGNMENT);
        bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        bottom.add(new FlatButton("关闭", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::dispose));
        root.add(gap(8));
        root.add(bottom);

        setContentPane(root);
        timer = new Timer(500, e -> refreshStatus());
        timer.start();
        refreshStatus();
    }

    private void syncGear() {
        for (int i = 0; i < gearChips.length; i++) gearChips[i].setSelected(engine.gear == i + 1);
    }

    private void refreshStatus() {
        if (closed) return;
        syncGear();
        status.setText(String.format("活跃注入 %d 条 · 当前 %s · 速度 %d×",
                engine.activeInjections(), engine.time().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")), engine.gear));
    }

    private void inject() {
        String sym = (String) symBox.getSelectedItem();
        double pct = num(movePct, 0);
        int min = (int) num(moveMin, 60);
        if (sym == null || pct == 0 || min <= 0) {
            status.setText("注入参数无效");
            return;
        }
        engine.injectMove(sym, pct, min);
        status.setText(String.format("已注入：%s %+.1f%% / %d 分钟", sym, pct, min));
    }

    // ------------------------------------------------------------------
    private JLabel sectionTitle(String s) {
        JLabel l = UI.label(s, 11, Theme.I.muted, true);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 6, 0));
        return l;
    }

    private JTextField field(String val) {
        return field(val, 88);
    }

    private JTextField field(String val, int w) {
        JTextField f = UI.field(val);
        f.setPreferredSize(new Dimension(w, 26));
        return f;
    }

    private JComponent row(String label, JTextField f, String btn, Runnable apply) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        p.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = UI.label(label, 11, Theme.I.muted, false);
        l.setPreferredSize(new Dimension(96, 20));
        p.add(l, BorderLayout.WEST);
        p.add(f, BorderLayout.CENTER);
        FlatButton b = new FlatButton(btn, () -> Theme.I.panelAlt, () -> Theme.I.text, apply);
        b.setPreferredSize(new Dimension(56, 26));
        p.add(b, BorderLayout.EAST);
        f.addActionListener(e -> apply.run());
        return p;
    }

    private Component gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(10, h));
        p.setPreferredSize(new Dimension(10, h));
        return p;
    }

    private static double num(JTextField f, double def) {
        try {
            return Double.parseDouble(f.getText().trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void dispose() {
        closed = true;
        timer.stop();
        super.dispose();
    }
}
