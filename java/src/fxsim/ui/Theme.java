package fxsim.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * 全局主题。所有界面颜色 / 字体 / 图表开关都从这里取，改完调用 {@link #changed()} 即可实时生效。
 * 配置保存在 %USERPROFILE%\.fxsim\theme.properties，可以直接用记事本改。
 */
public final class Theme {
    public static final Theme I = new Theme();

    public String name = "终端黑";

    public Color bg = Color.decode("#0B0E13");
    public Color panel = Color.decode("#12161E");
    public Color panelAlt = Color.decode("#171C26");
    public Color border = Color.decode("#232936");
    public Color text = Color.decode("#D8DEE9");
    public Color muted = Color.decode("#7A8494");
    public Color up = Color.decode("#2ECC8F");
    public Color down = Color.decode("#FF5C5C");
    public Color accent = Color.decode("#4C8DFF");
    public Color accent2 = Color.decode("#F0B429");
    public Color grid = Color.decode("#1B2130");
    public Color selection = Color.decode("#1E2634");

    public String fontFamily;
    public float scale = 1.0f;

    /** candles / bars / line */
    public String chartStyle = "candles";
    public boolean gridOn = true;
    public boolean crosshairOn = true;
    public boolean maOn = true;
    public boolean markersOn = true;
    public int maFast = 21;
    public int maSlow = 55;

    private final List<Runnable> listeners = new ArrayList<>();

    private Theme() {
        fontFamily = pickFont();
    }

    private static String pickFont() {
        String[] cands = {"Microsoft YaHei UI", "Microsoft YaHei", "微软雅黑", "Segoe UI", "Dialog"};
        Set<String> fams = new LinkedHashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String c : cands) {
            if (fams.contains(c)) return c;
        }
        return "Dialog";
    }

    // ------------------------------------------------------------------
    public Font font(int size, boolean bold) {
        return new Font(fontFamily, bold ? Font.BOLD : Font.PLAIN, Math.max(9, Math.round(size * scale)));
    }

    public Font font(float size) {
        return font(Math.round(size), false);
    }

    public void onChanged(Runnable r) {
        listeners.add(r);
    }

    public void changed() {
        for (Runnable r : new ArrayList<>(listeners)) {
            try {
                r.run();
            } catch (RuntimeException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    public static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ------------------------------------------------------------------
    /** 配置放在程序目录下的 config/，方便直接改，也不受用户目录权限影响 */
    public static Path home() {
        return Paths.get("config");
    }

    public static Path file() {
        return home().resolve("theme.properties");
    }

    public void load() {
        Path p = file();
        if (!Files.exists(p)) {
            save();
            return;
        }
        Properties pr = new Properties();
        try (InputStream in = Files.newInputStream(p)) {
            pr.load(in);
        } catch (IOException e) {
            return;
        }
        name = pr.getProperty("theme.name", name);
        bg = col(pr, "ui.bg", bg);
        panel = col(pr, "ui.panel", panel);
        panelAlt = col(pr, "ui.panelAlt", panelAlt);
        border = col(pr, "ui.border", border);
        text = col(pr, "ui.text", text);
        muted = col(pr, "ui.muted", muted);
        up = col(pr, "ui.up", up);
        down = col(pr, "ui.down", down);
        accent = col(pr, "ui.accent", accent);
        accent2 = col(pr, "ui.accent2", accent2);
        grid = col(pr, "ui.grid", grid);
        selection = col(pr, "ui.selection", selection);

        String fam = pr.getProperty("ui.font", fontFamily);
        if (fam != null && !fam.isBlank()) fontFamily = fam;
        try {
            scale = Float.parseFloat(pr.getProperty("ui.scale", "1.0"));
        } catch (NumberFormatException ignored) {
            scale = 1f;
        }
        chartStyle = pr.getProperty("chart.style", chartStyle);
        gridOn = bool(pr, "chart.grid", gridOn);
        crosshairOn = bool(pr, "chart.crosshair", crosshairOn);
        maOn = bool(pr, "chart.ma", maOn);
        markersOn = bool(pr, "chart.markers", markersOn);
        maFast = num(pr, "chart.maFast", maFast);
        maSlow = num(pr, "chart.maSlow", maSlow);
    }

    public void save() {
        Properties pr = new Properties();
        pr.setProperty("theme.name", name);
        pr.setProperty("ui.bg", hex(bg));
        pr.setProperty("ui.panel", hex(panel));
        pr.setProperty("ui.panelAlt", hex(panelAlt));
        pr.setProperty("ui.border", hex(border));
        pr.setProperty("ui.text", hex(text));
        pr.setProperty("ui.muted", hex(muted));
        pr.setProperty("ui.up", hex(up));
        pr.setProperty("ui.down", hex(down));
        pr.setProperty("ui.accent", hex(accent));
        pr.setProperty("ui.accent2", hex(accent2));
        pr.setProperty("ui.grid", hex(grid));
        pr.setProperty("ui.selection", hex(selection));
        pr.setProperty("ui.font", fontFamily);
        pr.setProperty("ui.scale", String.valueOf(scale));
        pr.setProperty("chart.style", chartStyle);
        pr.setProperty("chart.grid", String.valueOf(gridOn));
        pr.setProperty("chart.crosshair", String.valueOf(crosshairOn));
        pr.setProperty("chart.ma", String.valueOf(maOn));
        pr.setProperty("chart.markers", String.valueOf(markersOn));
        pr.setProperty("chart.maFast", String.valueOf(maFast));
        pr.setProperty("chart.maSlow", String.valueOf(maSlow));
        try {
            Files.createDirectories(home());
            try (var w = Files.newBufferedWriter(file(), java.nio.charset.StandardCharsets.UTF_8)) {
                w.write("# FX Sim 主题配置（UTF-8，可以用记事本直接改）\n");
                w.write("# 颜色写成 #RRGGBB；改完点设置里的“重新载入”，或重启程序。\n");
                w.write("# 字体用系统字体名，例如 Microsoft YaHei UI；ui.scale 是整体缩放（1.0 = 100%）。\n\n");
                pr.store(w, null);
            }
        } catch (IOException ex) {
            System.err.println("保存主题配置失败: " + ex);
        }
    }

    private static Color col(Properties p, String k, Color d) {
        String v = p.getProperty(k);
        if (v == null) return d;
        try {
            return Color.decode(v.trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }

    private static int num(Properties p, String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(d)).trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    // ------------------------------------------------------------------
    public static final String[] PRESETS = {"终端黑", "午夜蓝", "石墨", "琥珀终端", "松绿"};

    public void applyPreset(String preset) {
        name = preset;
        switch (preset) {
            case "午夜蓝" -> {
                bg = Color.decode("#0A1020"); panel = Color.decode("#0F1830"); panelAlt = Color.decode("#14203C");
                border = Color.decode("#1E2C4A"); text = Color.decode("#DCE6F5"); muted = Color.decode("#7C8CA6");
                up = Color.decode("#35D399"); down = Color.decode("#FF6B6B"); accent = Color.decode("#5B8CFF");
                accent2 = Color.decode("#FFB020"); grid = Color.decode("#16223C"); selection = Color.decode("#1A2742");
            }
            case "石墨" -> {
                bg = Color.decode("#151515"); panel = Color.decode("#1D1D1D"); panelAlt = Color.decode("#242424");
                border = Color.decode("#303030"); text = Color.decode("#E6E6E6"); muted = Color.decode("#8C8C8C");
                up = Color.decode("#57C785"); down = Color.decode("#E5533D"); accent = Color.decode("#7FA6F0");
                accent2 = Color.decode("#D7A65A"); grid = Color.decode("#262626"); selection = Color.decode("#2B2B2B");
            }
            case "琥珀终端" -> {
                bg = Color.decode("#0C0A06"); panel = Color.decode("#141008"); panelAlt = Color.decode("#1A1409");
                border = Color.decode("#2A2213"); text = Color.decode("#E8C88A"); muted = Color.decode("#8A7448");
                up = Color.decode("#9BCB6A"); down = Color.decode("#FF7A45"); accent = Color.decode("#FFB000");
                accent2 = Color.decode("#EFD18A"); grid = Color.decode("#221B0E"); selection = Color.decode("#241C0D");
            }
            case "松绿" -> {
                bg = Color.decode("#08110F"); panel = Color.decode("#0D1815"); panelAlt = Color.decode("#11201C");
                border = Color.decode("#1B2E28"); text = Color.decode("#D6E5DF"); muted = Color.decode("#6F8A81");
                up = Color.decode("#27C08A"); down = Color.decode("#E06060"); accent = Color.decode("#2FB6A8");
                accent2 = Color.decode("#D9B65A"); grid = Color.decode("#14251F"); selection = Color.decode("#16251F");
            }
            default -> {
                bg = Color.decode("#0B0E13"); panel = Color.decode("#12161E"); panelAlt = Color.decode("#171C26");
                border = Color.decode("#232936"); text = Color.decode("#D8DEE9"); muted = Color.decode("#7A8494");
                up = Color.decode("#2ECC8F"); down = Color.decode("#FF5C5C"); accent = Color.decode("#4C8DFF");
                accent2 = Color.decode("#F0B429"); grid = Color.decode("#1B2130"); selection = Color.decode("#1E2634");
            }
        }
    }
}
