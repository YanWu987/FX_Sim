package fxsim.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.Locale;

/** 一组画界面用的小工具，保证所有面板风格一致。 */
public final class UI {
    private UI() {
    }

    public static String money(double v) {
        return String.format(Locale.US, "%,.2f", v);
    }

    public static String money(double v, int digits) {
        return String.format(Locale.US, "%,." + digits + "f", v);
    }

    public static String signed(double v) {
        return String.format(Locale.US, "%+,.2f", v);
    }

    public static String pct(double v) {
        return String.format(Locale.US, "%+.2f%%", v);
    }

    public static void card(Graphics2D g, int x, int y, int w, int h, Color fill, Color border, int radius) {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, radius, radius);
        if (border != null) {
            g.setColor(border);
            g.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
        }
    }

    public static void text(Graphics2D g, String s, int x, int y, Color c, Font f) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, x, y);
    }

    public static JLabel label(String s, int size, Color color, boolean bold) {
        JLabel l = new JLabel(s);
        l.setForeground(color);
        l.setFont(Theme.I.font(size, bold));
        return l;
    }

    public static JTextField field(String text) {
        Theme t = Theme.I;
        JTextField f = new JTextField(text);
        f.setBackground(t.panelAlt);
        f.setForeground(t.text);
        f.setCaretColor(t.accent);
        f.setFont(t.font(12, false));
        f.setBorder(new EmptyBorder(5, 8, 5, 8));
        f.setHorizontalAlignment(SwingConstants.RIGHT);
        return f;
    }

    public static JScrollPane scroll(Component view) {
        Theme t = Theme.I;
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(t.panel);
        sp.setBackground(t.panel);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        sp.getVerticalScrollBar().setBackground(t.panelAlt);
        sp.getHorizontalScrollBar().setBackground(t.panelAlt);
        return sp;
    }

    public static void styleTable(JTable table) {
        Theme t = Theme.I;
        table.setBackground(t.panel);
        table.setForeground(t.text);
        table.setGridColor(Theme.mix(t.panel, t.border, 0.55));
        table.setSelectionBackground(t.selection);
        table.setSelectionForeground(t.text);
        table.setRowHeight(Math.round(25 * t.scale));
        table.setFont(t.font(12, false));
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);

        JTableHeader h = table.getTableHeader();
        h.setBackground(t.panelAlt);
        h.setForeground(t.muted);
        h.setFont(t.font(11, true));
        h.setBorder(BorderFactory.createEmptyBorder());
        h.setPreferredSize(new Dimension(10, Math.round(28 * t.scale)));
        h.setDefaultRenderer(headerRenderer(false));
    }

    public static DefaultTableCellRenderer headerRenderer(boolean right) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                Theme th = Theme.I;
                l.setBackground(th.panelAlt);
                l.setForeground(th.muted);
                l.setFont(th.font(11, true));
                l.setBorder(new EmptyBorder(0, right ? 8 : 8, 0, right ? 8 : 8));
                l.setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                return l;
            }
        };
    }

    public static DefaultTableCellRenderer cellRenderer(java.util.function.Function<Object, Color> colorFn,
                                                        boolean right) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                Theme th = Theme.I;
                l.setFont(th.font(12, false));
                l.setBorder(new EmptyBorder(0, 8, 0, 8));
                l.setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
                Color c = colorFn == null ? null : colorFn.apply(value);
                l.setForeground(selected ? th.text : (c == null ? th.text : c));
                l.setBackground(selected ? th.selection : th.panel);
                l.setOpaque(true);
                return l;
            }
        };
    }

    public static void applyUI() {
        Theme t = Theme.I;
        UIManager.put("Panel.background", t.panel);
        UIManager.put("Viewport.background", t.panel);
        UIManager.put("Label.foreground", t.text);
        UIManager.put("Label.font", t.font(12, false));
        UIManager.put("TextField.background", t.panelAlt);
        UIManager.put("TextField.foreground", t.text);
        UIManager.put("TextField.caretForeground", t.accent);
        UIManager.put("TextField.font", t.font(12, false));
        UIManager.put("TextArea.background", t.panel);
        UIManager.put("TextArea.foreground", t.text);
        UIManager.put("TextArea.font", new Font("Consolas", Font.PLAIN, Math.round(12 * t.scale)));
        UIManager.put("Table.background", t.panel);
        UIManager.put("Table.foreground", t.text);
        UIManager.put("Table.font", t.font(12, false));
        UIManager.put("Table.selectionBackground", t.selection);
        UIManager.put("Table.selectionForeground", t.text);
        UIManager.put("TableHeader.background", t.panelAlt);
        UIManager.put("TableHeader.foreground", t.muted);
        UIManager.put("ScrollPane.background", t.panel);
        UIManager.put("TabbedPane.background", t.panel);
        UIManager.put("TabbedPane.foreground", t.muted);
        UIManager.put("TabbedPane.selected", t.panelAlt);
        UIManager.put("TabbedPane.contentAreaColor", t.panel);
        UIManager.put("TabbedPane.font", t.font(12, false));
        UIManager.put("ComboBox.background", t.panelAlt);
        UIManager.put("ComboBox.foreground", t.text);
        UIManager.put("ComboBox.selectionBackground", t.selection);
        UIManager.put("ComboBox.selectionForeground", t.text);
        UIManager.put("ComboBox.font", t.font(12, false));
        UIManager.put("SplitPane.background", t.bg);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.thumb", t.border);
        UIManager.put("ScrollBar.track", t.panel);
        UIManager.put("ToolTip.background", t.panelAlt);
        UIManager.put("ToolTip.foreground", t.text);
        UIManager.put("OptionPane.background", t.panel);
        UIManager.put("OptionPane.messageForeground", t.text);
        UIManager.put("Button.background", t.panelAlt);
        UIManager.put("Button.foreground", t.text);
        UIManager.put("CheckBox.background", t.panel);
        UIManager.put("CheckBox.foreground", t.text);
        UIManager.put("RadioButton.background", t.panel);
        UIManager.put("RadioButton.foreground", t.text);
        UIManager.put("Slider.background", t.panel);
        UIManager.put("Spinner.background", t.panelAlt);
        UIManager.put("Spinner.foreground", t.text);
    }

    /** 主题切换后重新套一遍颜色 */
    public static void restyle(Component c) {
        Theme t = Theme.I;
        if (c instanceof JComponent jc) jc.setFont(t.font(12, false));
        if (c instanceof JLabel l) {
            l.setForeground(t.text);
            l.setFont(t.font(12, false));
        } else if (c instanceof JTextField f) {
            f.setBackground(t.panelAlt);
            f.setForeground(t.text);
            f.setCaretColor(t.accent);
            f.setFont(t.font(12, false));
        } else if (c instanceof JTextArea a) {
            a.setBackground(t.panel);
            a.setForeground(t.text);
        } else if (c instanceof JTable tb) {
            styleTable(tb);
        } else if (c instanceof JScrollPane sp) {
            sp.getViewport().setBackground(t.panel);
            sp.setBackground(t.panel);
        } else if (c instanceof JTabbedPane tp) {
            tp.setBackground(t.panel);
            tp.setForeground(t.muted);
            tp.setFont(t.font(12, false));
        } else if (c instanceof JCheckBox cb) {
            cb.setBackground(t.panel);
            cb.setForeground(t.text);
            cb.setFont(t.font(12, false));
        } else if (c instanceof JPanel p) {
            p.setBackground(t.panel);
        }
        if (c instanceof Container ct) {
            for (Component ch : ct.getComponents()) restyle(ch);
        }
        if (c instanceof JComponent jc) jc.repaint();
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
