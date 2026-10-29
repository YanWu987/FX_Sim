package fxsim.ui;

import javax.swing.*;
import java.awt.*;

/** 状态条：标题 + 进度条 + 数值。worseWhenHigh 用于"越高越糟"的指标（困意）。 */
public final class MiniBar extends JComponent {
    private final String label;
    private final Color color;
    private final boolean worseWhenHigh;
    private double value = 100;

    public MiniBar(String label, Color color) {
        this(label, color, false);
    }

    public MiniBar(String label, Color color, boolean worseWhenHigh) {
        this.label = label;
        this.color = color;
        this.worseWhenHigh = worseWhenHigh;
        setOpaque(false);
        setPreferredSize(new Dimension(94, 22));
    }

    public void set(double v) {
        if (Math.abs(v - value) < 0.05) return;
        value = v;
        repaint();
    }

    public double value() {
        return value;
    }

    /** 当前数值是否落在危险区间：生命/饱食/口渴看低值，困意看高值 */
    private boolean danger() {
        return worseWhenHigh ? value >= 75 : value < 25;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Theme t = Theme.I;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(t.font(9, false));
        g.setColor(t.muted);
        g.drawString(label, 0, 11);

        int bx = 27, bw = 48, by = 3, bh = 7;
        g.setColor(Theme.mix(t.panel, t.border, 0.6));
        g.fillRoundRect(bx, by, bw, bh, 4, 4);
        double p = Math.max(0, Math.min(1, value / 100));
        boolean bad = danger();
        g.setColor(bad ? Theme.mix(t.down, color, 0.35) : color);
        g.fillRoundRect(bx, by, Math.max(1, (int) Math.round(bw * p)), bh, 4, 4);

        g.setFont(t.font(9, true));
        g.setColor(bad ? t.down : t.text);
        g.drawString(String.valueOf((int) Math.round(value)), bx + bw + 5, 11);
        g.dispose();
    }
}
