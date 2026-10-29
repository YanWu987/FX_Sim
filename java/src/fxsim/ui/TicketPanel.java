package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.Instrument;
import fxsim.core.MarketEngine;
import fxsim.core.PairState;
import fxsim.core.PendingOrder;
import fxsim.core.Side;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Locale;

/** 下单面板 */
public final class TicketPanel extends JPanel {
    private final MarketEngine engine;
    private Account account;

    private String symbol = "EUR/USD";
    private int orderType;  // 0 市价 1 限价 2 突破

    private final JLabel symLabel = UI.label("", 15, Theme.I.text, true);
    private final JLabel priceLabel = UI.label("", 12, Theme.I.muted, false);
    private final JLabel infoLabel = UI.label("", 11, Theme.I.muted, false);
    private final JLabel errLabel = UI.label(" ", 10, Theme.I.accent2, false);

    private final JTextField lotsField = UI.field("0.10");
    private final JTextField triggerField = UI.field("");
    private final JTextField slField = UI.field("0");
    private final JTextField tpField = UI.field("0");

    private final JPanel triggerRow;
    private final Chip[] typeChips;
    private final FlatButton buyBtn;
    private final FlatButton sellBtn;
    private final JComboBox<String> levBox;
    private final JPanel levRow;

    public TicketPanel(MarketEngine engine, Account account) {
        this.engine = engine;
        this.account = account;
        Theme t = Theme.I;
        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(t.panel);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        // ---- 标题
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(symLabel, BorderLayout.WEST);
        head.add(priceLabel, BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        body.add(head);
        body.add(Box.createVerticalStrut(8));

        // ---- 杠杆
        levRow = new JPanel(new BorderLayout(8, 0));
        levRow.setOpaque(false);
        levRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel levLab = UI.label("杠杆", 11, t.muted, false);
        levLab.setPreferredSize(new Dimension(46, 20));
        levRow.add(levLab, BorderLayout.WEST);
        levBox = new JComboBox<>(new String[]{"1:1", "1:10", "1:50", "1:100", "1:500", "1:1000"});
        levBox.setSelectedItem("1:" + (int) account.leverage);
        levBox.setBackground(t.panelAlt);
        levBox.setForeground(t.text);
        levBox.setFont(t.font(11, false));
        levBox.setFocusable(false);
        levBox.setToolTipText("杠杆只影响之后新开的仓位");
        levBox.addActionListener(e -> {
            Object sel = levBox.getSelectedItem();
            if (sel != null) account.setLeverage(Double.parseDouble(sel.toString().substring(2)));
            updateInfo();
        });
        levRow.add(levBox, BorderLayout.CENTER);
        body.add(levRow);
        body.add(Box.createVerticalStrut(8));

        // ---- 手数
        JPanel lotsRow = new JPanel(new BorderLayout(6, 0));
        lotsRow.setOpaque(false);
        lotsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel lotsLab = UI.label("手数", 11, t.muted, false);
        lotsLab.setPreferredSize(new Dimension(46, 20));
        lotsRow.add(lotsLab, BorderLayout.WEST);

        JPanel lotsCtl = new JPanel(new BorderLayout(6, 0));
        lotsCtl.setOpaque(false);
        FlatButton minus = new FlatButton("−", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> bumpLots(-0.01));
        FlatButton plus = new FlatButton("+", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> bumpLots(0.01));
        minus.setPreferredSize(new Dimension(34, 28));
        plus.setPreferredSize(new Dimension(34, 28));
        lotsCtl.add(minus, BorderLayout.WEST);
        lotsCtl.add(lotsField, BorderLayout.CENTER);
        lotsCtl.add(plus, BorderLayout.EAST);
        lotsRow.add(lotsCtl, BorderLayout.CENTER);
        body.add(lotsRow);
        body.add(Box.createVerticalStrut(10));

        // ---- 类型
        JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        typeRow.setOpaque(false);
        typeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel typeLab = UI.label("类型", 11, t.muted, false);
        typeLab.setPreferredSize(new Dimension(34, 20));
        typeRow.add(typeLab);
        String[] names = {"市价", "限价", "突破"};
        typeChips = new Chip[names.length];
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            typeChips[i] = new Chip(names[i], i == 0, () -> setType(idx));
            typeRow.add(typeChips[i]);
        }
        body.add(typeRow);
        body.add(Box.createVerticalStrut(8));

        // ---- 触发价
        triggerRow = row("触发价", triggerField);
        triggerRow.setVisible(false);
        body.add(triggerRow);

        // ---- 止损止盈
        JPanel sltp = new JPanel(new GridLayout(1, 2, 8, 0));
        sltp.setOpaque(false);
        sltp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        sltp.add(row("止损点", slField));
        sltp.add(row("止盈点", tpField));
        body.add(sltp);
        body.add(Box.createVerticalStrut(8));

        infoLabel.setAlignmentX(LEFT_ALIGNMENT);
        body.add(infoLabel);
        body.add(Box.createVerticalStrut(10));

        // ---- 买卖按钮
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 0));
        btns.setOpaque(false);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        final Color buyFg = new Color(6, 30, 20);
        buyBtn = new FlatButton("买入 BUY", () -> Theme.I.up, () -> buyFg, () -> place(Side.BUY));
        sellBtn = new FlatButton("卖出 SELL", () -> Theme.I.down, () -> Color.WHITE, () -> place(Side.SELL));
        btns.add(buyBtn);
        btns.add(sellBtn);
        body.add(btns);
        body.add(Box.createVerticalStrut(8));

        FlatButton closeSym = new FlatButton("平掉本品种全部持仓", () -> Theme.I.panelAlt, () -> Theme.I.muted,
                () -> account.closeAll(symbol));
        closeSym.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        body.add(closeSym);
        body.add(Box.createVerticalStrut(4));

        errLabel.setAlignmentX(LEFT_ALIGNMENT);
        errLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        body.add(errLabel);

        add(body, BorderLayout.NORTH);

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                updateInfo();
            }

            public void removeUpdate(DocumentEvent e) {
                updateInfo();
            }

            public void changedUpdate(DocumentEvent e) {
                updateInfo();
            }
        };
        lotsField.getDocument().addDocumentListener(dl);
        triggerField.getDocument().addDocumentListener(dl);
        refresh();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.min(302, d.width), d.height);
    }

    private JPanel row(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = UI.label(label, 11, Theme.I.muted, false);
        l.setPreferredSize(new Dimension(52, 20));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    public String symbol() {
        return symbol;
    }

    public void setSymbol(String s) {
        symbol = s;
        PairState ps = engine.pair(s);
        if (triggerField.getText().isBlank()) triggerField.setText(ps.ins.fmt(ps.mid));
        refresh();
    }

    public void setType(int idx) {
        orderType = idx;
        for (int i = 0; i < typeChips.length; i++) typeChips[i].setSelected(i == idx);
        triggerRow.setVisible(idx != 0);
        if (idx != 0) {
            PairState ps = engine.pair(symbol);
            triggerField.setText(ps.ins.fmt(ps.mid));
        }
        revalidate();
        updateInfo();
    }

    private void bumpLots(double d) {
        double v = num(lotsField, 0.1) + d;
        v = Math.max(0.01, Math.min(50, Math.round(v * 100) / 100.0));
        lotsField.setText(String.format(Locale.US, "%.2f", v));
    }

    /** 切换市场时换账户 */
    public void setAccount(Account a) {
        account = a;
        boolean fx = a.asset == Instrument.Asset.FX;
        levBox.setEnabled(fx);
        levRow.setVisible(fx);
        revalidate();
        levBox.setToolTipText(fx ? "杠杆只影响之后新开的仓位" : "期货按保证金率计算，杠杆不适用");
        refresh();
    }

    /** 每帧调用：刷新报价、保证金估算 */
    public void refresh() {
        PairState ps = engine.pair(symbol);
        symLabel.setText(symbol);
        priceLabel.setText(String.format("%s  %s", ps.ins.fmt(ps.bid()), ps.ins.fmt(ps.ask())));
        priceLabel.setForeground(Theme.I.muted);
        updateInfo();
    }

    private void updateInfo() {
        PairState ps = engine.pair(symbol);
        double lots = Math.max(0.01, num(lotsField, 0.1));
        double margin = account.marginRequired(symbol, lots);
        double pip = ps.ins.pipValueUsd(ps.mid) * lots;
        infoLabel.setText(String.format("保证金 %s USD   ·   每点 %s USD", UI.money(margin), UI.money(pip, 2)));
        infoLabel.setForeground(Theme.I.muted);
    }

    private void place(Side side) {
        PairState ps = engine.pair(symbol);
        double lots = Math.max(0.01, Math.min(50, num(lotsField, 0.1)));
        double slPips = Math.max(0, num(slField, 0));
        double tpPips = Math.max(0, num(tpField, 0));
        double pip = ps.ins.pipSize;

        boolean ok;
        if (orderType == 0) {
            double entry = side == Side.BUY ? ps.ask() : ps.bid();
            double sl = slPips > 0 ? entry - side.dir * slPips * pip : 0;
            double tp = tpPips > 0 ? entry + side.dir * tpPips * pip : 0;
            ok = account.openMarket(symbol, side, lots, sl, tp);
        } else {
            double trig = num(triggerField, ps.mid);
            double sl = slPips > 0 ? trig - side.dir * slPips * pip : 0;
            double tp = tpPips > 0 ? trig + side.dir * tpPips * pip : 0;
            ok = account.placePending(symbol, side, lots,
                    orderType == 1 ? PendingOrder.Type.LIMIT : PendingOrder.Type.STOP, trig, sl, tp);
        }
        if (!ok) {
            errLabel.setText("× " + (account.lastError.isEmpty() ? "下单失败" : account.lastError));
            errLabel.setForeground(Theme.I.down);
        } else {
            errLabel.setText("√ 已提交");
            errLabel.setForeground(Theme.I.up);
            Timer timer = new Timer(1800, e -> errLabel.setText(" "));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private static double num(JTextField f, double def) {
        try {
            return Double.parseDouble(f.getText().trim());
        } catch (Exception e) {
            return def;
        }
    }
}
