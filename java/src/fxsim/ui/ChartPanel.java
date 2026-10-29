package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.Timeframe;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/** 图表容器：负责缩放、拖动、十字光标。 */
public final class ChartPanel extends JPanel {
    private final ChartRenderer renderer;
    private String symbol = "EUR/USD";
    private Timeframe tf = Timeframe.H1;
    private Point mouse;
    private int dragX;
    private int dragOffset;
    private boolean dragging;

    public ChartPanel(MarketEngine engine, Account account) {
        renderer = new ChartRenderer(engine, account);
        setOpaque(true);
        setBackground(Theme.I.bg);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    if (!dragging) {
                        dragging = true;
                        dragX = e.getX();
                        dragOffset = renderer.offset;
                    }
                    int dx = e.getX() - dragX;
                    renderer.offset = Math.max(0, Math.min(4000, dragOffset + dx / 6));
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                mouse = e.getPoint();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragging = false;
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragging = false;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouse = null;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    renderer.offset = 0;
                    renderer.bars = 110;
                    repaint();
                }
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                int d = e.getWheelRotation();
                renderer.bars = Math.max(25, Math.min(600, renderer.bars + d * 12));
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    public String symbol() {
        return symbol;
    }

    public void setAccount(fxsim.core.Account a) {
        renderer.setAccount(a);
        repaint();
    }

    public void setSymbol(String s) {
        this.symbol = s;
        this.renderer.offset = 0;
        repaint();
    }

    public Timeframe timeframe() {
        return tf;
    }

    public void setTimeframe(Timeframe tf) {
        this.tf = tf;
        this.renderer.offset = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.mouse = mouse;
        renderer.crosshair = Theme.I.crosshairOn;
        renderer.paint((Graphics2D) g, 0, 0, getWidth(), getHeight(), symbol, tf);
    }
}
