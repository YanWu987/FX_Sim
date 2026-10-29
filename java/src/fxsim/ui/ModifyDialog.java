package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.PairState;
import fxsim.core.Position;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** 修改持仓的止损 / 止盈（按真实规则校验：不能贴着现价） */
public final class ModifyDialog extends JDialog {
    private final MarketEngine engine;
    private final Account account;
    private final Position pos;
    private final JTextField slField;
    private final JTextField tpField;
    private final JLabel error = UI.label(" ", 10, Theme.I.down, false);

    public ModifyDialog(Frame owner, MarketEngine engine, Account account, Position p) {
        super(owner, "修改持仓 #" + p.id, true);
        this.engine = engine;
        this.account = account;
        this.pos = p;
        Theme t = Theme.I;
        setSize(390, 330);
        setResizable(false);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel();
        root.setBackground(t.panel);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(16, 18, 16, 18));

        PairState st = engine.pair(p.symbol);
        JLabel title = UI.label(p.symbol + "  " + p.side.en + "  " + String.format("%.2f 手", p.lots), 14, t.text, true);
        title.setAlignmentX(LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(4));

        JLabel info = UI.label(String.format("开仓价 %s     现价 %s / %s", st.ins.fmt(p.open),
                st.ins.fmt(st.bid()), st.ins.fmt(st.ask())), 11, t.muted, false);
        info.setAlignmentX(LEFT_ALIGNMENT);
        root.add(info);
        root.add(Box.createVerticalStrut(16));

        slField = UI.field(p.sl > 0 ? st.ins.fmt(p.sl) : "");
        tpField = UI.field(p.tp > 0 ? st.ins.fmt(p.tp) : "");
        root.add(row("止损价", slField));
        root.add(Box.createVerticalStrut(10));
        root.add(row("止盈价", tpField));
        root.add(Box.createVerticalStrut(8));

        JLabel hint = UI.label("留空 = 不设置；价格距现价至少 2 点，否则会被立刻触发", 10, t.muted, false);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        root.add(hint);
        root.add(Box.createVerticalStrut(8));

        error.setAlignmentX(LEFT_ALIGNMENT);
        error.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        root.add(error);
        root.add(Box.createVerticalStrut(12));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btns.add(new FlatButton("确定", () -> Theme.I.accent, () -> Color.WHITE, this::apply));
        btns.add(new FlatButton("清除", () -> Theme.I.panelAlt, () -> Theme.I.muted, () -> {
            if (account.modify(pos, 0, 0)) dispose();
            else error.setText(account.lastError);
        }));
        btns.add(new FlatButton("取消", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::dispose));
        root.add(btns);

        setContentPane(root);
    }

    private JPanel row(String label, JTextField field) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = UI.label(label, 11, Theme.I.muted, false);
        l.setPreferredSize(new Dimension(64, 20));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private void apply() {
        double sl = parse(slField.getText());
        double tp = parse(tpField.getText());
        if (sl < 0 || tp < 0) {
            error.setText("价格填写有误");
            return;
        }
        String err = account.validateStops(pos, sl, tp);
        if (err != null) {
            error.setText(err);
            return;
        }
        if (account.modify(pos, sl, tp)) dispose();
        else error.setText(account.lastError);
    }

    private static double parse(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
