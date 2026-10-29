package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.Player;

import javax.swing.*;
import java.awt.*;

/** 顶部账户栏 */
public final class HeaderPanel extends JPanel {
    private final MarketEngine engine;
    private Account account;
    private final Player player;

    private final Metric balance = new Metric("余额 BALANCE");
    private final Metric equity = new Metric("净值 EQUITY");
    private final Metric margin = new Metric("已用保证金 MARGIN");
    private final Metric free = new Metric("可用 FREE");
    private final Metric level = new Metric("保证金水平 LEVEL");
    private final Metric debt = new Metric("负债 DEBT");

    public HeaderPanel(MarketEngine engine, Account account, Player player,
                       Runnable onSettings, Runnable onReset, Runnable onLife, Runnable onTutorial) {
        this.engine = engine;
        this.account = account;
        this.player = player;
        Theme t = Theme.I;
        setOpaque(true);
        setLayout(new BorderLayout(16, 0));
        setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        setPreferredSize(new Dimension(0, 82));

        add(new Brand(), BorderLayout.WEST);

        JPanel metrics = new JPanel(new GridLayout(1, 6, 12, 0));
        metrics.setOpaque(false);
        metrics.add(balance);
        metrics.add(equity);
        metrics.add(margin);
        metrics.add(free);
        metrics.add(level);
        metrics.add(debt);
        add(metrics, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        right.setOpaque(false);
        FlatButton tut = new FlatButton("教程", () -> Theme.I.panelAlt, () -> Theme.I.text, onTutorial);
        FlatButton life = new FlatButton("生活", () -> Theme.I.accent2, () -> Color.WHITE, onLife);
        FlatButton settings = new FlatButton("界面设置", () -> Theme.I.panelAlt, () -> Theme.I.text, onSettings);
        FlatButton reset = new FlatButton("重置账户", () -> Theme.alpha(Theme.I.down, 40), () -> Theme.I.down, onReset);
        right.add(tut);
        right.add(life);
        right.add(settings);
        right.add(reset);
        add(right, BorderLayout.EAST);

        refresh();
    }

    /** 切换市场时换账户 */
    public void setAccount(Account a) {
        account = a;
        refresh();
    }

    public void refresh() {
        double eq = account.equity();
        double ml = account.marginLevel();
        balance.set(UI.money(account.balance), Theme.I.text);
        equity.set(UI.money(eq), eq >= account.balance ? Theme.I.up : Theme.I.down);
        margin.set(UI.money(account.marginUsed()), Theme.I.text);
        free.set(UI.money(account.freeMargin()), account.freeMargin() >= 0 ? Theme.I.text : Theme.I.down);
        Color lc = ml <= 0 ? Theme.I.muted : ml < 120 ? Theme.I.down : ml < 300 ? Theme.I.accent2 : Theme.I.up;
        level.set(ml <= 0 ? "—" : String.format("%.0f%%", ml), lc);
        debt.set(UI.money(player.debt), player.debt > 0 ? Theme.I.down : Theme.I.muted);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Theme t = Theme.I;
        g.setPaint(new GradientPaint(0, 0, Theme.mix(t.panel, t.bg, 0.25), 0, getHeight(), t.panel));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(t.border);
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        g.dispose();
    }

    /** 单个指标块 */
    private static final class Metric extends JComponent {
        private final String title;
        private String value = "—";
        private Color color = Theme.I.text;

        Metric(String title) {
            this.title = title;
            setOpaque(false);
        }

        void set(String v, Color c) {
            if (v.equals(value) && c.equals(color)) return;
            value = v;
            color = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Theme t = Theme.I;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(t.font(9, false));
            g.setColor(t.muted);
            g.drawString(title, 0, 14);
            g.setFont(t.font(17, true));
            g.setColor(color);
            g.drawString(value, 0, 40);
            g.dispose();
        }
    }

    /** 左上角标题 */
    private static final class Brand extends JComponent {
        Brand() {
            setPreferredSize(new Dimension(168, 60));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Theme t = Theme.I;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(t.accent);
            g.fillRoundRect(0, 8, 34, 34, 9, 9);
            g.setColor(t.bg);
            g.setFont(t.font(15, true));
            g.drawString("FX", 8, 31);
            g.setColor(t.text);
            g.setFont(t.font(17, true));
            g.drawString("SIM", 42, 26);
            g.setColor(t.muted);
            g.setFont(t.font(9, false));
            g.drawString("外汇模拟交易终端", 42, 40);
            g.dispose();
        }
    }
}
