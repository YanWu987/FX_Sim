package fxsim.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

/** 自绘扁平按钮。颜色可以用 Supplier 提供，这样切换主题时会自动跟随。 */
public final class FlatButton extends JComponent {
    private String text;
    private Supplier<Color> base;
    private Supplier<Color> fg;
    private Runnable action;
    private boolean hover;
    private boolean press;
    private final int radius = 7;

    public FlatButton(String text, Color base, Color fg, Runnable action) {
        this(text, () -> base, () -> fg, action);
    }

    public FlatButton(String text, Supplier<Color> base, Supplier<Color> fg, Runnable action) {
        this.text = text;
        this.base = base;
        this.fg = fg;
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
                press = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                press = true;
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                boolean fire = press && contains(e.getPoint());
                press = false;
                repaint();
                if (fire) fire();
            }
        });
    }

    public void fire() {
        if (action != null) action.run();
    }

    public void setAction(Runnable a) {
        this.action = a;
    }

    public void setText(String s) {
        this.text = s;
        revalidate();
        repaint();
    }

    public void setBase(Color c) {
        this.base = () -> c;
        repaint();
    }

    public void setFg(Color c) {
        this.fg = () -> c;
        repaint();
    }

    public void setBase(Supplier<Color> s) {
        this.base = s;
        repaint();
    }

    public void setFg(Supplier<Color> s) {
        this.fg = s;
        repaint();
    }

    public String getText() {
        return text;
    }

    @Override
    public Dimension getPreferredSize() {
        Font f = Theme.I.font(12, true);
        FontMetrics fm = getFontMetrics(f);
        int w = fm.stringWidth(text == null ? "" : text) + 26;
        int h = Math.max(28, fm.getHeight() + 10);
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(w, d.width), Math.max(h, d.height));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Theme t = Theme.I;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color fill = base != null ? base.get() : t.panelAlt;
        if (fill == null) fill = t.panelAlt;
        if (press) fill = fill.darker();
        else if (hover) fill = Theme.mix(fill, Color.WHITE, 0.10);
        UI.card(g2, 0, 0, getWidth(), getHeight(), fill, null, radius);
        if (!isEnabled()) {
            g2.setColor(Theme.alpha(t.bg, 140));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        }
        Color textColor = fg != null ? fg.get() : t.text;
        if (textColor == null) textColor = t.text;
        Font f = t.font(12, true);
        g2.setFont(f);
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int tx = (getWidth() - fm.stringWidth(text == null ? "" : text)) / 2;
        int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
        g2.drawString(text == null ? "" : text, tx, ty);
        g2.dispose();
    }
}
