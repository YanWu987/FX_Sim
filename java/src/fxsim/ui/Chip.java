package fxsim.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** 可选中的小标签按钮（周期切换、开关）。 */
public final class Chip extends JComponent {
    private String text;
    private boolean selected;
    private Runnable action;
    private boolean hover;

    public Chip(String text, boolean selected, Runnable action) {
        this.text = text;
        this.selected = selected;
        this.action = action;
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFocusable(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (action != null) action.run();
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setAction(Runnable r) {
        this.action = r;
    }

    public void setSelected(boolean s) {
        selected = s;
        repaint();
    }

    public void setText(String s) {
        text = s;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = Theme.I.font(12, true);
        FontMetrics fm = getFontMetrics(f);
        return new Dimension(fm.stringWidth(text) + 20, Math.max(26, fm.getHeight() + 8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Theme t = Theme.I;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill;
        Color fg;
        if (selected) {
            fill = Theme.alpha(t.accent, 42);
            fg = t.accent;
        } else {
            fill = hover ? t.panelAlt : Theme.mix(t.panel, t.panelAlt, 0.5);
            fg = hover ? t.text : t.muted;
        }
        UI.card(g2, 0, 0, getWidth(), getHeight(), fill, selected ? Theme.alpha(t.accent, 120) : null, 6);
        g2.setFont(t.font(12, true));
        g2.setColor(fg);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
