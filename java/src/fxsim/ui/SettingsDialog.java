package fxsim.ui;

import fxsim.core.MarketEngine;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 界面自定义面板：主题预设、配色、字体、字号、图表样式。改动即时生效。 */
public final class SettingsDialog extends JDialog {
    private final List<Object[]> swatches = new ArrayList<>();  // {FlatButton, Supplier<Color>}
    private final JComboBox<String> fontBox;
    private final JSlider scaleSlider;
    private final JLabel scaleLabel;
    private final JPanel root;

    public SettingsDialog(Frame owner, MarketEngine engine) {
        super(owner, "界面自定义", false);
        Theme t = Theme.I;
        setSize(600, 680);
        setResizable(false);
        setLocationRelativeTo(owner);

        root = new JPanel();
        root.setBackground(t.panel);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel head = UI.label("界面自定义", 15, t.text, true);
        head.setAlignmentX(LEFT_ALIGNMENT);
        root.add(head);
        JLabel sub = UI.label("所有改动立即生效；点“保存配置”写入 config/theme.properties，也可以直接用记事本改这个文件", 10, t.muted, false);
        sub.setAlignmentX(LEFT_ALIGNMENT);
        root.add(sub);
        root.add(gap(16));

        // ---------------- 预设 ----------------
        JPanel presets = section("主题预设");
        for (String p : Theme.PRESETS) {
            presets.add(new Chip(p, p.equals(t.name), () -> {
                t.applyPreset(p);
                t.save();
                refreshSwatches();
                t.changed();
            }));
        }

        // ---------------- 配色 ----------------
        title("配色（点色块修改）");
        JPanel grid = new JPanel(new GridLayout(2, 3, 8, 8));
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        grid.add(swatch("背景", () -> t.bg, c -> t.bg = c));
        grid.add(swatch("面板", () -> t.panel, c -> t.panel = c));
        grid.add(swatch("强调色", () -> t.accent, c -> t.accent = c));
        grid.add(swatch("上涨", () -> t.up, c -> t.up = c));
        grid.add(swatch("下跌", () -> t.down, c -> t.down = c));
        grid.add(swatch("文字", () -> t.text, c -> t.text = c));
        root.add(grid);
        root.add(gap(16));

        // ---------------- 字体 ----------------
        JPanel fontRow = section("字体与字号");
        String[] fonts = {"Microsoft YaHei UI", "微软雅黑", "SimHei", "SimSun", "Consolas", "Segoe UI", "Dialog"};
        fontBox = new JComboBox<>(fonts);
        fontBox.setSelectedItem(t.fontFamily);
        fontBox.setBackground(t.panelAlt);
        fontBox.setForeground(t.text);
        fontBox.setPreferredSize(new Dimension(200, 28));
        fontBox.addActionListener(e -> {
            Object sel = fontBox.getSelectedItem();
            if (sel != null) {
                t.fontFamily = sel.toString();
                t.changed();
            }
        });
        fontRow.add(fontBox);
        scaleSlider = new JSlider(80, 150, Math.round(t.scale * 100));
        scaleSlider.setOpaque(false);
        scaleSlider.setPreferredSize(new Dimension(160, 28));
        scaleLabel = UI.label(Math.round(t.scale * 100) + "%", 11, t.muted, false);
        scaleSlider.addChangeListener(e -> {
            t.scale = scaleSlider.getValue() / 100f;
            scaleLabel.setText(scaleSlider.getValue() + "%");
            t.changed();
        });
        fontRow.add(scaleSlider);
        fontRow.add(scaleLabel);

        // ---------------- 图表 ----------------
        JPanel styleRow = section("图表样式");
        styleRow.add(UI.label("K 线", 11, t.muted, false));
        JComboBox<String> styleBox = new JComboBox<>(new String[]{"蜡烛图", "线图", "美国线"});
        styleBox.setSelectedIndex("line".equals(t.chartStyle) ? 1 : "bars".equals(t.chartStyle) ? 2 : 0);
        styleBox.setBackground(t.panelAlt);
        styleBox.setForeground(t.text);
        styleBox.setPreferredSize(new Dimension(110, 28));
        styleBox.addActionListener(e -> {
            t.chartStyle = switch (styleBox.getSelectedIndex()) {
                case 1 -> "line";
                case 2 -> "bars";
                default -> "candles";
            };
            t.changed();
        });
        styleRow.add(styleBox);
        styleRow.add(Box.createHorizontalStrut(14));
        styleRow.add(toggle("网格", t.gridOn, v -> t.gridOn = v));
        styleRow.add(toggle("十字光标", t.crosshairOn, v -> t.crosshairOn = v));
        styleRow.add(toggle("均线", t.maOn, v -> t.maOn = v));
        styleRow.add(toggle("持仓标记", t.markersOn, v -> t.markersOn = v));

        JPanel maRow = section("均线周期");
        JTextField fast = UI.field(String.valueOf(t.maFast));
        fast.setPreferredSize(new Dimension(56, 26));
        JTextField slow = UI.field(String.valueOf(t.maSlow));
        slow.setPreferredSize(new Dimension(56, 26));
        Runnable applyMa = () -> {
            t.maFast = Math.max(2, parseInt(fast.getText(), t.maFast));
            t.maSlow = Math.max(3, parseInt(slow.getText(), t.maSlow));
            t.changed();
        };
        fast.addActionListener(e -> applyMa.run());
        slow.addActionListener(e -> applyMa.run());
        maRow.add(fast);
        maRow.add(UI.label("/", 11, t.muted, false));
        maRow.add(slow);
        maRow.add(UI.label("（回车生效）", 10, t.muted, false));

        // ---------------- 模拟规则 ----------------
        JPanel rules = section("模拟规则");
        rules.add(toggle("周末休市（周五 23:00 – 周日 23:00）", engine.weekendClose, v -> {
            engine.weekendClose = v;
            engine.say(v ? "已开启周末休市" : "已关闭周末休市，行情 24/7 连续");
        }));

        root.add(gap(10));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        actions.add(new FlatButton("保存配置", () -> Theme.I.accent, () -> Color.WHITE, () -> {
            t.save();
            JOptionPane.showMessageDialog(this, "已保存到\n" + Theme.file());
        }));
        actions.add(new FlatButton("重新载入", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> {
            t.load();
            t.changed();
            refreshSwatches();
            fontBox.setSelectedItem(t.fontFamily);
            scaleSlider.setValue(Math.round(t.scale * 100));
        }));
        actions.add(new FlatButton("关闭", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::dispose));
        root.add(actions);

        JScrollPane sp = new JScrollPane(root);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(t.panel);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        setContentPane(sp);
    }

    // ------------------------------------------------------------------
    private void title(String s) {
        JLabel l = UI.label(s, 11, Theme.I.muted, true);
        l.setAlignmentX(LEFT_ALIGNMENT);
        l.setBorder(new EmptyBorder(0, 0, 7, 0));
        root.add(l);
    }

    private JPanel section(String title) {
        title(title);
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        root.add(p);
        root.add(gap(16));
        return p;
    }

    private Chip toggle(String label, boolean sel, Consumer<Boolean> set) {
        boolean[] state = {sel};
        Chip[] holder = new Chip[1];
        Chip c = new Chip(label, sel, () -> {
            state[0] = !state[0];
            holder[0].setSelected(state[0]);
            set.accept(state[0]);
            Theme.I.changed();
        });
        holder[0] = c;
        return c;
    }

    private void refreshSwatches() {
        for (Object[] s : swatches) {
            FlatButton b = (FlatButton) s[0];
            @SuppressWarnings("unchecked")
            Supplier<Color> get = (Supplier<Color>) s[1];
            Color c = get.get();
            b.setBase(c);
            b.setFg(contrast(c));
            b.repaint();
        }
        repaint();
    }

    private FlatButton swatch(String label, Supplier<Color> get, Consumer<Color> set) {
        Color c = get.get();
        FlatButton b = new FlatButton(label, c, contrast(c), null);
        b.setAction(() -> {
            Color picked = JColorChooser.showDialog(this, "选择 " + label, get.get());
            if (picked != null) {
                set.accept(picked);
                b.setBase(picked);
                b.setFg(contrast(picked));
                Theme.I.changed();
            }
        });
        swatches.add(new Object[]{b, get});
        return b;
    }

    private static Color contrast(Color c) {
        double lum = (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
        return lum > 0.6 ? new Color(20, 20, 22) : Color.WHITE;
    }

    private Component gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(10, h));
        p.setPreferredSize(new Dimension(10, h));
        return p;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
