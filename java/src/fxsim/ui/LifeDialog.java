package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.Player;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

/**
 * 生活面板：生命值 / 饱食度 / 口渴度、商店（买了就吃）、贷款。
 * 打开期间游戏时间照常走，数值会实时刷新。
 */
public final class LifeDialog extends JDialog {
    private final MarketEngine engine;
    private final Account account;
    private final Player player;

    private final MiniBar hpBar = new MiniBar("生命", Theme.I.up);
    private final MiniBar foodBar = new MiniBar("饱食", Theme.I.accent2);
    private final MiniBar waterBar = new MiniBar("口渴", Theme.I.accent);
    private final MiniBar sleepBar = new MiniBar("困意", Theme.mix(Theme.I.accent, Theme.I.down, 0.45), true);
    private final JLabel countLabel = UI.label("", 10, Theme.I.muted, false);
    private final JLabel debtLabel = UI.label("", 11, Theme.I.muted, false);
    private final JLabel timeLabel = UI.label("", 10, Theme.I.muted, false);
    private final JLabel msg = UI.label(" ", 10, Theme.I.muted, false);
    private boolean closed;
    private final Timer timer;

    public LifeDialog(Frame owner, MarketEngine engine, Account account, Player player) {
        super(owner, "生活 · 商店 · 贷款", false);
        this.engine = engine;
        this.account = account;
        this.player = player;
        Theme t = Theme.I;
        setSize(480, 560);
        setResizable(false);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel();
        root.setBackground(t.panel);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel head = UI.label("生活状态", 15, t.text, true);
        head.setAlignmentX(LEFT_ALIGNMENT);
        root.add(head);
        timeLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(timeLabel);
        root.add(Box.createVerticalStrut(12));

        JPanel bars = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bars.setOpaque(false);
        bars.setAlignmentX(LEFT_ALIGNMENT);
        bars.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        bars.add(hpBar);
        bars.add(foodBar);
        bars.add(waterBar);
        bars.add(sleepBar);
        root.add(bars);
        countLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(countLabel);
        root.add(Box.createVerticalStrut(4));

        JLabel tip = UI.label("饱食和口渴都 ≥ 50 时生命值回升；口渴见底 5 天、饥饿见底 10 天会倒下", 10, t.muted, false);
        tip.setAlignmentX(LEFT_ALIGNMENT);
        root.add(tip);
        root.add(Box.createVerticalStrut(14));

        root.add(sectionTitle("商店（买了立刻吃喝）"));
        JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        for (Player.Item it : Player.SHOP) {
            StringBuilder sb = new StringBuilder(it.name()).append("  $").append((int) it.cost());
            if (it.satiety() > 0) sb.append("  +").append((int) it.satiety()).append(" 饱");
            if (it.thirst() > 0) sb.append("  +").append((int) it.thirst()).append(" 渴");
            if (it.drowsy() != 0) sb.append("  ").append((int) it.drowsy()).append(" 困");
            grid.add(new FlatButton(sb.toString(), () -> Theme.I.panelAlt, () -> Theme.I.text, () -> buy(it)));
        }
        root.add(grid);
        root.add(Box.createVerticalStrut(16));

        root.add(sectionTitle("贷款"));
        debtLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(debtLabel);
        root.add(Box.createVerticalStrut(8));

        JPanel loans = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        loans.setOpaque(false);
        loans.setAlignmentX(LEFT_ALIGNMENT);
        loans.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        loans.add(new FlatButton("借 5,000", () -> Theme.I.accent, () -> Color.WHITE, () -> borrow(5000)));
        loans.add(new FlatButton("借 10,000", () -> Theme.I.accent, () -> Color.WHITE, () -> borrow(10000)));
        loans.add(new FlatButton("还 5,000", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> repay(5000)));
        loans.add(new FlatButton("全部还清", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> repay(player.debt)));
        JPanel sleepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sleepRow.setOpaque(false);
        sleepRow.setAlignmentX(LEFT_ALIGNMENT);
        sleepRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        sleepRow.add(new FlatButton("睡觉（跳过 1 天）", () -> Theme.I.accent2, () -> Color.WHITE, this::doSleep));
        root.add(sleepRow);
        root.add(loans);
        root.add(Box.createVerticalStrut(10));

        msg.setAlignmentX(LEFT_ALIGNMENT);
        msg.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        root.add(msg);
        root.add(Box.createVerticalStrut(10));

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bottom.setOpaque(false);
        bottom.setAlignmentX(LEFT_ALIGNMENT);
        bottom.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bottom.add(new FlatButton("关闭", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::dispose));
        root.add(bottom);

        setContentPane(root);
        timer = new Timer(400, e -> refresh());
        timer.start();
        refresh();
    }

    private JLabel sectionTitle(String s) {
        JLabel l = UI.label(s, 11, Theme.I.muted, true);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    public void refresh() {
        if (closed) return;
        hpBar.set(player.hp);
        foodBar.set(player.satiety);
        waterBar.set(player.thirst);
        sleepBar.set(player.drowsiness);
        countLabel.setText(String.format("本次未睡：咖啡 %d/5 · 能量饮料 %d/3", player.coffeeCount, player.energyCount));
        timeLabel.setText(engine.time().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + "   ·   余额 " + Player.money(account.balance) + "   ·   明日利息 "
                + Player.money(player.dailyInterest()));
        debtLabel.setText(String.format("负债 %s / 额度 %s    日息 %.2f%%",
                Player.money(player.debt), Player.money(Player.MAX_LOAN), Player.DAILY_RATE * 100));
    }

    private void doSleep() {
        int r = JOptionPane.showConfirmDialog(this,
                "睡觉会跳过整整一天，期间行情照走、仓位无法管理（止损和强平照常触发）。确定？",
                "睡觉", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        player.sleep(account);
        refresh();
        show(null);
    }

    private void buy(Player.Item it) {
        String err = player.buy(it, account);
        show(err);
    }

    private void borrow(double amt) {
        show(player.borrow(amt, account));
    }

    private void repay(double amt) {
        show(player.repay(amt, account));
    }

    private void show(String err) {
        msg.setText(err == null ? "✓ 完成" : "× " + err);
        msg.setForeground(err == null ? Theme.I.up : Theme.I.down);
        refresh();
    }

    @Override
    public void dispose() {
        closed = true;
        if (timer != null) timer.stop();
        super.dispose();
    }
}
