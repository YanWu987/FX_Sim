package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.Candle;
import fxsim.core.MarketEngine;
import fxsim.core.PairState;
import fxsim.core.Position;
import fxsim.core.Timeframe;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

/** K 线图渲染器（可离屏调用，方便截图检查）。 */
public final class ChartRenderer {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MDHM = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MarketEngine engine;
    private Account account;

    public int bars = 110;
    public int offset = 0;
    public Point mouse;
    public boolean crosshair = true;

    public ChartRenderer(MarketEngine engine, Account account) {
        this.engine = engine;
        this.account = account;
    }

    public void setAccount(Account a) {
        this.account = a;
    }

    public void paint(Graphics2D g0, int x, int y, int w, int h, String symbol, Timeframe tf) {
        Theme t = Theme.I;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(t.bg);
        g.fillRect(x, y, w, h);

        PairState ps = engine.pair(symbol);
        List<Candle> all = ps.series(tf);

        int axisW = 78;
        int axisH = 26;
        int top = 34;
        Rectangle2D plot = new Rectangle2D.Double(x + 10, y + top, w - axisW - 18, h - axisH - top - 10);

        // 顶部信息条
        drawLegend(g, x, y, w, symbol, tf, ps, all);

        if (all.size() < 2 || plot.getWidth() < 60 || plot.getHeight() < 60) {
            g.setColor(t.muted);
            g.setFont(t.font(12, false));
            g.drawString("正在生成行情…", x + 16, y + h / 2);
            g.dispose();
            return;
        }

        int n = all.size();
        int end = Math.max(2, n - Math.max(0, offset));
        int start = Math.max(0, end - Math.max(10, bars));
        int count = end - start;

        // ---------------- 价格范围 ----------------
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int i = start; i < end; i++) {
            Candle c = all.get(i);
            lo = Math.min(lo, c.l);
            hi = Math.max(hi, c.h);
        }
        lo = Math.min(lo, ps.mid);
        hi = Math.max(hi, ps.mid);

        double span0 = Math.max(hi - lo, ps.ins.pipSize);
        if (t.markersOn) {
            for (Position p : account.positions) {
                if (!p.symbol.equals(symbol)) continue;
                double[] lv = {p.open, p.sl, p.tp};
                for (double v : lv) {
                    if (v <= 0) continue;
                    // 离得太远的止损/止盈不参与缩放，避免把 K 线压扁
                    if (v > lo - span0 * 0.3 && v < hi + span0 * 0.3) {
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                }
            }
        }
        double pad = Math.max((hi - lo) * 0.07, ps.ins.pipSize * 2);
        final double flo = lo - pad;
        final double fhi = hi + pad;
        final double span = Math.max(fhi - flo, ps.ins.pipSize);
        final double px = plot.getX(), py = plot.getY(), pw = plot.getWidth(), ph = plot.getHeight();
        DoubleUnaryOperator Y = v -> py + (fhi - v) / span * ph;

        // ---------------- 网格 ----------------
        float yNowPre = (float) Y.applyAsDouble(ps.mid);
        if (t.gridOn) {
            g.setStroke(new BasicStroke(1f));
            g.setColor(t.grid);
            double step = pipStep(span, ps.ins.pipSize);
            double first = Math.ceil(flo / step) * step;
            int guard = 0;
            for (double p = first; p < fhi && guard++ < 40; p += step) {
                float yy = (float) Y.applyAsDouble(p);
                if (yy < py + 1 || yy > py + ph - 1) continue;
                g.drawLine((int) px, Math.round(yy), (int) (px + pw), Math.round(yy));
                // 现价色带附近不画刻度，避免文字被压住
                if (Math.abs(yy - yNowPre) > 11) {
                    g.setColor(t.muted);
                    g.setFont(t.font(10, false));
                    drawRight(g, ps.ins.fmt(p), (float) (x + w - 8), yy + 4);
                    g.setColor(t.grid);
                }
            }
        }

        // 时间竖线
        double cw = pw / count;
        int every = Math.max(1, (int) Math.ceil(96 / cw));
        g.setColor(t.grid);
        for (int i = start; i < end; i += every) {
            float xx = (float) (px + (i - start) * cw);
            g.drawLine(Math.round(xx), (int) py, Math.round(xx), (int) (py + ph));
        }

        // ---------------- 价格曲线 ----------------
        if (!engine.marketOpen()) {
            g.setFont(t.font(48, true));
            g.setColor(Theme.alpha(t.muted, 26));
            String s = "休市";
            int tw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, (float) (px + (pw - tw) / 2), (float) (py + ph / 2));
        }
        if ("line".equals(t.chartStyle)) {
            drawLineChart(g, all, start, end, cw, plot, Y, t);
        } else {
            drawCandles(g, all, start, end, cw, plot, Y, t, "bars".equals(t.chartStyle));
        }

        // ---------------- 均线 ----------------
        if (t.maOn && all.size() > t.maSlow) {
            drawMa(g, all, start, end, cw, plot, Y, t.maSlow, Theme.alpha(t.accent2, 190), 1.4f);
            drawMa(g, all, start, end, cw, plot, Y, t.maFast, Theme.alpha(t.accent, 210), 1.2f);
        }

        // ---------------- 持仓标记 ----------------
        if (t.markersOn) drawPositions(g, symbol, plot, Y, t, w, x);

        // ---------------- 现价 ----------------
        float yNow = (float) Y.applyAsDouble(ps.mid);
        if (yNow > py && yNow < py + ph) {
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{4, 4}, 0));
            g.setColor(Theme.alpha(t.accent, 200));
            g.drawLine((int) px, Math.round(yNow), (int) (px + pw), Math.round(yNow));
            g.setStroke(new BasicStroke(1f));
            // 右侧整条价格轴做成色带，避免压住刻度文字
            g.setColor(t.accent);
            g.fillRect(x + w - axisW + 2, Math.round(yNow) - 8, axisW - 4, 16);
            g.setFont(t.font(10, true));
            g.setColor(Color.WHITE);
            String now = ps.ins.fmt(ps.mid);
            g.drawString(now, x + w - 8 - g.getFontMetrics().stringWidth(now), yNow + 4);
        }

        // ---------------- 坐标轴底 ----------------
        g.setColor(t.panel);
        int axisTop = (int) Math.round(py + ph);
        g.fillRect(x + w - axisW, axisTop, axisW, y + h - axisTop);
        g.fillRect(x + w - axisW, y + top, 1, h - top);
        g.setColor(t.border);
        g.drawLine((int) px, (int) Math.round(py + ph), (int) (px + pw), (int) Math.round(py + ph));

        // 时间标签
        g.setColor(t.muted);
        g.setFont(t.font(10, false));
        int labels = Math.max(2, (int) (pw / 110));
        for (int k = 0; k <= labels; k++) {
            int i = start + (int) Math.round((count - 1) * (k / (double) labels));
            if (i >= end) i = end - 1;
            Candle c = all.get(i);
            float xx = (float) (px + (i - start) * cw);
            String s = timeLabel(c.t, tf);
            int tw = g.getFontMetrics().stringWidth(s);
            float tx = (float) Math.max(px, Math.min(xx - tw / 2f, px + pw - tw));
            g.drawString(s, tx, (float) (py + ph + 18));
        }

        // ---------------- 十字光标 ----------------
        if (crosshair && mouse != null && plot.contains(mouse)) {
            drawCrosshair(g, plot, Y, flo, span, all, start, end, cw, ps, tf, t, x, w, h, y, axisW, axisH);
        }

        g.setColor(t.border);
        g.drawRect(x, y, w - 1, h - 1);
        g.dispose();
    }

    // ------------------------------------------------------------------
    private void drawLegend(Graphics2D g, int x, int y, int w, String symbol, Timeframe tf, PairState ps, List<Candle> all) {
        Theme t = Theme.I;
        Candle c = all.isEmpty() ? null : all.get(all.size() - 1);
        g.setFont(t.font(13, true));
        g.setColor(t.text);
        int cx = x + 14;
        g.drawString(symbol, cx, y + 20);
        cx += g.getFontMetrics().stringWidth(symbol) + 10;

        g.setFont(t.font(11, true));
        g.setColor(t.accent);
        g.drawString(tf.shortLabel, cx, y + 20);

        if (c != null) {
            g.setFont(t.font(11, false));
            g.setColor(t.muted);
            String ohlc = String.format("O %s   H %s   L %s   C %s",
                    ps.ins.fmt(c.o), ps.ins.fmt(c.h), ps.ins.fmt(c.l), ps.ins.fmt(c.c));
            g.drawString(ohlc, x + 14, y + 32);

            double chg = (c.c - c.o);
            String arrow = chg >= 0 ? "▲" : "▼";
            g.setColor(chg >= 0 ? t.up : t.down);
            g.drawString(arrow + " " + ps.ins.fmt(Math.abs(chg)) + " (" + UI.pct(chg / c.o * 100) + ")", cx + 40, y + 20);
        }

        g.setFont(t.font(10, false));
        g.setColor(t.muted);
        String sp = String.format("点差 %.1f · 高 %s · 低 %s", ps.spreadPips, ps.ins.fmt(ps.dayHigh), ps.ins.fmt(ps.dayLow));
        g.drawString(sp, x + w - 12 - g.getFontMetrics().stringWidth(sp), y + 20);
    }

    private void drawCandles(Graphics2D g, List<Candle> all, int start, int end, double cw,
                             Rectangle2D plot, DoubleUnaryOperator Y, Theme t, boolean bars) {
        float bodyW = (float) Math.max(1.0, Math.min(bars ? 1.5 : 11, cw * (bars ? 0.15 : 0.62)));
        g.setStroke(new BasicStroke(1f));
        for (int i = start; i < end; i++) {
            Candle c = all.get(i);
            float cx = (float) (plot.getX() + (i - start + 0.5) * cw);
            float yO = (float) Y.applyAsDouble(c.o);
            float yC = (float) Y.applyAsDouble(c.c);
            float yH = (float) Y.applyAsDouble(c.h);
            float yL = (float) Y.applyAsDouble(c.l);
            Color col = c.up() ? t.up : t.down;
            g.setColor(col);
            if (bars) {
                g.drawLine(Math.round(cx), Math.round(yH), Math.round(cx), Math.round(yO));
                g.drawLine(Math.round(cx) - 3, Math.round(yO), Math.round(cx) + 3, Math.round(yO));
                g.drawLine(Math.round(cx), Math.round(yL), Math.round(cx), Math.round(yC));
                g.drawLine(Math.round(cx) - 3, Math.round(yC), Math.round(cx) + 3, Math.round(yC));
            } else {
                g.drawLine(Math.round(cx), Math.round(yH), Math.round(cx), Math.round(yL));
                float top = Math.min(yO, yC);
                float bot = Math.max(yO, yC);
                g.fillRect(Math.round(cx - bodyW / 2), Math.round(top), Math.max(1, Math.round(bodyW)),
                        Math.max(1, Math.round(bot - top)));
            }
        }
    }

    private void drawLineChart(Graphics2D g, List<Candle> all, int start, int end, double cw,
                               Rectangle2D plot, DoubleUnaryOperator Y, Theme t) {
        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
        java.awt.geom.Path2D.Float fill = new java.awt.geom.Path2D.Float();
        fill.moveTo(plot.getX(), plot.getY() + plot.getHeight());
        for (int i = start; i < end; i++) {
            float cx = (float) (plot.getX() + (i - start + 0.5) * cw);
            float cy = (float) Y.applyAsDouble(all.get(i).c);
            if (i == start) path.moveTo(cx, cy);
            else path.lineTo(cx, cy);
            fill.lineTo(cx, cy);
        }
        fill.lineTo(plot.getX() + (end - start) * cw, plot.getY() + plot.getHeight());
        fill.closePath();
        g.setColor(Theme.alpha(t.accent, 40));
        g.fill(fill);
        g.setColor(t.accent);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(path);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawMa(Graphics2D g, List<Candle> all, int start, int end, double cw,
                        Rectangle2D plot, DoubleUnaryOperator Y, int period, Color color, float width) {
        if (period < 2 || start < period) return;
        java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
        for (int i = start; i < end; i++) {
            double sum = 0;
            for (int k = 0; k < period; k++) sum += all.get(i - k).c;
            double avg = sum / period;
            float cx = (float) (plot.getX() + (i - start + 0.5) * cw);
            float cy = (float) Y.applyAsDouble(avg);
            if (i == start) path.moveTo(cx, cy);
            else path.lineTo(cx, cy);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setStroke(new BasicStroke(1f));
    }

    private void drawPositions(Graphics2D g, String symbol, Rectangle2D plot, DoubleUnaryOperator Y, Theme t, int w, int x) {
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{5, 4}, 0));
        for (Position p : account.positions) {
            if (!p.symbol.equals(symbol)) continue;
            Color col = p.side.dir > 0 ? t.up : t.down;
            float yy = (float) Y.applyAsDouble(p.open);
            if (yy > plot.getY() && yy < plot.getY() + plot.getHeight()) {
                g.setColor(Theme.alpha(col, 170));
                g.drawLine((int) plot.getX(), Math.round(yy), (int) (plot.getX() + plot.getWidth()), Math.round(yy));
                String s = (p.side == fxsim.core.Side.BUY ? "BUY " : "SELL ") + String.format("%.2f", p.lots);
                tagLeft(g, s, (int) plot.getX() + 6, Math.round(yy), col, t.bg);
            }
            if (p.sl > 0) markerLine(g, plot, Y, p.sl, t.down, "SL", t);
            if (p.tp > 0) markerLine(g, plot, Y, p.tp, t.up, "TP", t);
        }
        g.setStroke(new BasicStroke(1f));
    }

    private void markerLine(Graphics2D g, Rectangle2D plot, DoubleUnaryOperator Y, double price,
                            Color col, String label, Theme t) {
        float yy = (float) Y.applyAsDouble(price);
        if (yy <= plot.getY() || yy >= plot.getY() + plot.getHeight()) return;
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{2, 4}, 0));
        g.setColor(Theme.alpha(col, 150));
        g.drawLine((int) plot.getX(), Math.round(yy), (int) (plot.getX() + plot.getWidth()), Math.round(yy));
        g.setFont(t.font(9, true));
        g.setColor(col);
        g.drawString(label, (float) (plot.getX() + plot.getWidth() - 22), yy - 3);
    }

    private void drawCrosshair(Graphics2D g, Rectangle2D plot, DoubleUnaryOperator Y, double flo, double span,
                               List<Candle> all, int start, int end, double cw, PairState ps, Timeframe tf,
                               Theme t, int x, int w, int h, int y, int axisW, int axisH) {
        int mx = mouse.x, my = mouse.y;
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3, 3}, 0));
        g.setColor(Theme.alpha(t.text, 70));
        g.drawLine(mx, (int) plot.getY(), mx, (int) (plot.getY() + plot.getHeight()));
        g.drawLine((int) plot.getX(), my, (int) (plot.getX() + plot.getWidth()), my);
        g.setStroke(new BasicStroke(1f));

        double price = flo + (plot.getY() + plot.getHeight() - my) / plot.getHeight() * span;
        tag(g, ps.ins.fmt(price), x + w - 4, my, t.panelAlt, t.text, true);

        int idx = start + (int) ((mx - plot.getX()) / cw);
        idx = Math.max(start, Math.min(end - 1, idx));
        Candle c = all.get(idx);
        String s = engine.timeOf(c.t).format(tf == Timeframe.D1 ? MD : MDHM);
        g.setFont(t.font(10, false));
        int tw = g.getFontMetrics().stringWidth(s) + 12;
        int bx = Math.max((int) plot.getX(), Math.min(mx - tw / 2, (int) (plot.getX() + plot.getWidth()) - tw));
        int by = (int) (plot.getY() + plot.getHeight()) + 2;
        g.setColor(t.panelAlt);
        g.fillRoundRect(bx, by, tw, axisH - 4, 6, 6);
        g.setColor(t.text);
        g.drawString(s, bx + 6, by + 15);
    }

    private void tag(Graphics2D g, String s, int right, int cy, Color fill, Color fg, boolean rightAlign) {
        g.setFont(Theme.I.font(10, true));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(s) + 10;
        int th = 16;
        int bx = rightAlign ? right - tw : right;
        g.setColor(fill);
        g.fillRoundRect(bx, cy - th / 2, tw, th, 4, 4);
        g.setColor(fg);
        g.drawString(s, bx + 5, cy + fm.getAscent() / 2 - 1);
    }

    private void tagLeft(Graphics2D g, String s, int left, int cy, Color fill, Color fg) {
        g.setFont(Theme.I.font(9, true));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(s) + 8;
        g.setColor(Theme.alpha(fill, 210));
        g.fillRoundRect(left, cy - 7, tw, 14, 4, 4);
        g.setColor(fg);
        g.drawString(s, left + 4, cy + 3);
    }

    private void drawRight(Graphics2D g, String s, float rightX, float baseline) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, rightX - fm.stringWidth(s), baseline);
    }

    private String timeLabel(long minute, Timeframe tf) {
        LocalDateTime t = engine.timeOf(minute);
        if (tf == Timeframe.D1) return t.format(MD);
        if (tf == Timeframe.H4 || tf == Timeframe.H1) return t.format(MDHM);
        return t.format(HM);
    }

    /** 以"点"为单位的漂亮刻度：1/2/2.5/5/10/20/25/50/100… 点，保证 4~9 条网格线 */
    private static double pipStep(double span, double pip) {
        double target = span / 7.0 / pip;
        double[] ladder = {1, 2, 2.5, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 2500, 5000, 10000};
        for (double s : ladder) {
            if (s >= target) return s * pip;
        }
        return 10000 * pip;
    }
}
