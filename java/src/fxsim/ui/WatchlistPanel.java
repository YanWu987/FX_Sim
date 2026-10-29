package fxsim.ui;

import fxsim.core.MarketEngine;
import fxsim.core.PairState;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

/** 自选品种列表 */
public final class WatchlistPanel extends JPanel {
    private static final int ROW = 22;
    private static final int HEAD = 24;

    private final MarketEngine engine;
    private List<String> symbols;
    private String selected = "EUR/USD";
    private int hover = -1;
    private Consumer<String> onSelect;

    public WatchlistPanel(MarketEngine engine) {
        this.engine = engine;
        this.symbols = engine.symbols();
        setOpaque(true);
        setBackground(Theme.I.panel);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i >= 0 && i < symbols.size()) {
                    selected = symbols.get(i);
                    if (onSelect != null) onSelect.accept(selected);
                    repaint();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int i = rowAt(e.getY());
                if (i != hover) {
                    hover = i;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    /** 切换市场时换品种列表 */
    public void setSymbols(List<String> s) {
        symbols = s;
        hover = -1;
        selected = s.isEmpty() ? "" : s.get(0);
        revalidate();
        repaint();
    }

    public void onSelect(Consumer<String> c) {
        onSelect = c;
    }

    public String selected() {
        return selected;
    }

    public void setSelected(String s) {
        selected = s;
        repaint();
    }

    private int rowAt(int y) {
        if (y < HEAD) return -1;
        int i = (y - HEAD) / ROW;
        return i < symbols.size() ? i : -1;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(310, HEAD + symbols.size() * ROW + 10);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Theme t = Theme.I;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(t.panel);
        g.fillRect(0, 0, getWidth(), getHeight());

        int w = getWidth();
        g.setFont(t.font(10, true));
        g.setColor(t.muted);
        g.drawString("品种", 14, 18);
        drawRight(g, "买价", w - 152, 18);
        drawRight(g, "卖价", w - 80, 18);
        drawRight(g, "日内", w - 12, 18);

        g.setColor(Theme.mix(t.panel, t.border, 0.5));
        g.fillRect(10, HEAD - 6, w - 20, 1);

        for (int i = 0; i < symbols.size(); i++) {
            String s = symbols.get(i);
            PairState p = engine.pair(s);
            int y = HEAD + i * ROW;
            boolean sel = s.equals(selected);
            if (sel) {
                g.setColor(t.selection);
                g.fillRoundRect(6, y + 1, w - 12, ROW - 2, 6, 6);
                g.setColor(t.accent);
                g.fillRoundRect(6, y + 5, 3, ROW - 10, 3, 3);
            } else if (i == hover) {
                g.setColor(Theme.mix(t.panel, t.panelAlt, 0.8));
                g.fillRoundRect(6, y + 1, w - 12, ROW - 2, 6, 6);
            }

            g.setFont(t.font(12, true));
            g.setColor(sel ? t.text : Theme.mix(t.text, t.muted, 0.35));
            g.drawString(s, 16, y + 16);

            g.setFont(t.font(11, false));
            g.setColor(t.text);
            drawRight(g, p.ins.fmt(p.bid()), w - 152, y + 16);
            drawRight(g, p.ins.fmt(p.ask()), w - 80, y + 16);

            double chg = p.dayChangePct();
            g.setColor(chg >= 0 ? t.up : t.down);
            drawRight(g, UI.pct(chg), w - 12, y + 16);
        }
        g.setColor(t.border);
        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        g.dispose();
    }

    private void drawRight(Graphics2D g, String s, int right, int baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, right - fm.stringWidth(s), baseline);
    }
}
