package fxsim.ui;

import fxsim.core.MarketEngine;
import fxsim.core.Player;

import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;

/** 底部状态栏：游戏时间、新闻、速度控制、生活状态 */
public final class StatusBar extends JPanel {
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final java.time.format.DateTimeFormatter OPEN_FMT =
            java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MarketEngine engine;
    private final Player player;
    private final JLabel clock = UI.label("", 11, Theme.I.muted, false);
    private final JLabel news = UI.label("", 11, Theme.I.accent2, false);
    private final FlatButton pauseBtn;
    private final Chip[] chips;
    private final MiniBar hpBar = new MiniBar("生命", Theme.I.up);
    private final MiniBar foodBar = new MiniBar("饱食", Theme.I.accent2);
    private final MiniBar waterBar = new MiniBar("口渴", Theme.I.accent);
    private final MiniBar sleepyBar = new MiniBar("困意", Theme.mix(Theme.I.accent, Theme.I.down, 0.45), true);
    private static final int[] GEARS = {1, 2, 3, 4, 5};

    public StatusBar(MarketEngine engine, Player player) {
        this.engine = engine;
        this.player = player;
        Theme t = Theme.I;
        setOpaque(true);
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        setPreferredSize(new Dimension(0, 36));

        pauseBtn = new FlatButton("暂停", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> {
            engine.paused = !engine.paused;
            updatePause();
        });
        pauseBtn.setPreferredSize(new Dimension(84, 24));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(clock);
        left.add(Box.createHorizontalStrut(6));
        left.add(pauseBtn);

        chips = new Chip[GEARS.length];
        for (int i = 0; i < GEARS.length; i++) {
            int g = GEARS[i];
            chips[i] = new Chip(g + "×", engine.gear == g, () -> {
                engine.setGear(g);
                syncChips();
                refresh();
            });
            chips[i].setToolTipText("1 现实秒 = " + (int) (MarketEngine.BASE_SPEED * g) + " 游戏分钟");
            left.add(chips[i]);
        }
        add(left, BorderLayout.WEST);

        news.setHorizontalAlignment(SwingConstants.LEFT);
        add(news, BorderLayout.CENTER);

        JLabel hint = UI.label("SPACE 暂停", 10, Theme.I.muted, false);
        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        east.setOpaque(false);
        east.add(hpBar);
        east.add(foodBar);
        east.add(waterBar);
        east.add(sleepyBar);
        east.add(hint);
        add(east, BorderLayout.EAST);

        updatePause();
        refresh();
    }

    private void syncChips() {
        for (int i = 0; i < chips.length; i++) {
            chips[i].setSelected(engine.gear == GEARS[i]);
        }
    }

    private void updatePause() {
        pauseBtn.setText(engine.paused ? "继续" : "暂停");
        pauseBtn.setFg(() -> engine.paused ? Theme.I.accent2 : Theme.I.text);
    }

    public void togglePause() {
        engine.paused = !engine.paused;
        updatePause();
        refresh();
    }

    /** 键盘调速：+1 / -1 档 */
    public void stepGear(int delta) {
        engine.setGear(engine.gear + delta);
        syncChips();
        refresh();
    }

    public void refresh() {
        var t = engine.time();
        DayOfWeek d = t.getDayOfWeek();
        String clockText = String.format("%s %s %02d:%02d", t.toLocalDate(),
                WEEK[d.getValue() - 1], t.getHour(), t.getMinute());
        if (engine.marketOpen()) {
            clock.setText(clockText + String.format("  ·  速度 %d×（1 秒 = %d 分钟）",
                    engine.gear, (int) engine.speed));
            news.setText(engine.latestHeadline());
            news.setForeground(Theme.I.accent2);
        } else {
            clock.setText(clockText + "  ·  休市");
            news.setText("周末休市 · 下一开盘 " + engine.nextOpen().format(OPEN_FMT));
            news.setForeground(Theme.I.accent);
        }
        if (engine.paused && !pauseBtn.getText().equals("继续")) updatePause();
        hpBar.set(player.hp);
        foodBar.set(player.satiety);
        waterBar.set(player.thirst);
        sleepyBar.set(player.drowsiness);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Theme t = Theme.I;
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(t.panel);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(t.border);
        g.fillRect(0, 0, getWidth(), 1);
        g.dispose();
    }
}
