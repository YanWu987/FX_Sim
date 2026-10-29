package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;
import fxsim.core.PairState;
import fxsim.core.PendingOrder;
import fxsim.core.Position;
import fxsim.core.TradeRecord;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

/** 底部：持仓 / 挂单 / 历史 / 日志 */
public final class OrdersPanel extends JPanel {
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final MarketEngine engine;
    private Account account;
    private final Supplier<String> currentSymbol;

    private final DefaultTableModel posModel;
    private final DefaultTableModel pendModel;
    private final DefaultTableModel histModel;
    private final JTable posTable;
    private final JTable pendTable;
    private final JTable histTable;
    private final JTextArea logArea = new JTextArea();
    private final JTabbedPane tabs = new JTabbedPane();
    private int lastLogCount = -1;

    public OrdersPanel(MarketEngine engine, Account account, Supplier<String> currentSymbol) {
        this.engine = engine;
        this.account = account;
        this.currentSymbol = currentSymbol;
        Theme t = Theme.I;
        setLayout(new BorderLayout());
        setBackground(t.panel);

        // ---- 工具条
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bar.setOpaque(false);
        bar.add(new FlatButton("修改止损止盈", () -> Theme.I.panelAlt, () -> Theme.I.text, this::modifySelected));
        bar.add(new FlatButton("平仓选中", () -> Theme.I.panelAlt, () -> Theme.I.text, this::closeSelected));
        bar.add(new FlatButton("平掉本品种", () -> Theme.I.panelAlt, () -> Theme.I.text, () -> account.closeAll(currentSymbol.get())));
        bar.add(new FlatButton("全部平仓", () -> Theme.alpha(Theme.I.down, 46), () -> Theme.I.down, () -> account.closeAll(null)));
        bar.add(new FlatButton("取消挂单", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::cancelSelected));
        bar.add(new FlatButton("重置账户", () -> Theme.I.panelAlt, () -> Theme.I.muted, () -> account.reset()));
        add(bar, BorderLayout.NORTH);

        // ---- 表格
        posModel = model(new String[]{"#", "品种", "方向", "手数", "开仓", "现价", "止损", "止盈", "点数", "浮动盈亏", "隔夜"});
        pendModel = model(new String[]{"#", "品种", "类型", "方向", "手数", "触发价", "距离(点)", "止损", "止盈"});
        histModel = model(new String[]{"时间", "品种", "动作", "手数", "价格", "盈亏", "余额"});

        posTable = table(posModel);
        pendTable = table(pendModel);
        histTable = table(histModel);

        posTable.getColumnModel().getColumn(2).setCellRenderer(UI.cellRenderer(v -> {
            String s = String.valueOf(v);
            return s.startsWith("BUY") ? Theme.I.up : Theme.I.down;
        }, false));
        Color ignored = null;
        posTable.getColumnModel().getColumn(9).setCellRenderer(UI.cellRenderer(v -> {
            String s = String.valueOf(v);
            return s.startsWith("+") ? Theme.I.up : s.startsWith("-") ? Theme.I.down : Theme.I.text;
        }, true));
        histTable.getColumnModel().getColumn(5).setCellRenderer(UI.cellRenderer(v -> {
            String s = String.valueOf(v);
            return s.startsWith("+") ? Theme.I.up : s.startsWith("-") ? Theme.I.down : Theme.I.text;
        }, true));
        pendTable.getColumnModel().getColumn(3).setCellRenderer(UI.cellRenderer(v -> {
            String s = String.valueOf(v);
            return s.startsWith("BUY") ? Theme.I.up : Theme.I.down;
        }, false));

        widths(posTable, 46, 88, 56, 58, 90, 90, 88, 88, 66, 96, 70);
        widths(pendTable, 54, 96, 66, 64, 66, 100, 84, 92, 92);
        widths(histTable, 126, 96, 112, 68, 96, 100, 112);
        alignRight(posTable, 3, 4, 5, 6, 7, 8, 10);
        alignRight(pendTable, 4, 5, 6, 7, 8);
        alignRight(histTable, 3, 4, 6);

        posTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) closeSelected();
            }
        });

        tabs.setFont(t.font(12, false));
        tabs.addTab("持仓", UI.scroll(posTable));
        tabs.addTab("挂单", UI.scroll(pendTable));
        tabs.addTab("历史", UI.scroll(histTable));
        tabs.addTab("日志", UI.scroll(logArea));

        logArea.setEditable(false);
        logArea.setBackground(t.bg);
        logArea.setForeground(Theme.mix(t.muted, t.text, 0.55));
        logArea.setFont(new Font("Consolas", Font.PLAIN, Math.round(12 * t.scale)));
        logArea.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        add(tabs, BorderLayout.CENTER);
    }

    private DefaultTableModel model(String[] cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JTable table(DefaultTableModel m) {
        JTable t = new JTable(m);
        UI.styleTable(t);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.getTableHeader().setReorderingAllowed(false);
        return t;
    }

    private void widths(JTable t, int... w) {
        t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < w.length && i < t.getColumnCount(); i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    private void alignRight(JTable t, int... cols) {
        for (int c : cols) {
            t.getColumnModel().getColumn(c).setCellRenderer(UI.cellRenderer(null, true));
            t.getColumnModel().getColumn(c).setHeaderRenderer(UI.headerRenderer(true));
        }
    }

    private void modifySelected() {
        int row = posTable.getSelectedRow();
        if (row < 0 || row >= account.positions.size()) return;
        Position p = account.positions.get(row);
        Window w = SwingUtilities.getWindowAncestor(this);
        Frame owner = (w instanceof Frame f) ? f : null;
        new ModifyDialog(owner, engine, account, p).setVisible(true);
        refresh();
    }

    private void closeSelected() {
        int row = posTable.getSelectedRow();
        if (row < 0 || row >= account.positions.size()) return;
        account.close(account.positions.get(row), "手动");
        refresh();
    }

    private void cancelSelected() {
        int row = pendTable.getSelectedRow();
        if (row < 0 || row >= account.pending.size()) return;
        account.cancel(account.pending.get(row));
        refresh();
    }

    public void setAccount(Account a) {
        account = a;
        refresh();
    }

    public void refresh() {
        posModel.setRowCount(0);
        for (Position p : account.positions) {
            PairState ps = engine.pair(p.symbol);
            posModel.addRow(new Object[]{
                    "#" + p.id, p.symbol, p.side.en, String.format("%.2f", p.lots),
                    ps.ins.fmt(p.open), ps.ins.fmt(p.closePrice(engine)),
                    p.sl > 0 ? ps.ins.fmt(p.sl) : "—",
                    p.tp > 0 ? ps.ins.fmt(p.tp) : "—",
                    String.format("%+.1f", p.pips(engine)),
                    String.format("%+.2f", p.pnl(engine)),
                    String.format("%+.2f", p.swap)
            });
        }

        pendModel.setRowCount(0);
        for (PendingOrder o : account.pending) {
            PairState ps = engine.pair(o.symbol);
            double dist = (o.trigger - ps.mid) / ps.ins.pipSize;
            pendModel.addRow(new Object[]{
                    "#" + o.id, o.symbol, o.type.cn, o.side.en, String.format("%.2f", o.lots),
                    ps.ins.fmt(o.trigger), String.format("%+.0f", dist),
                    o.sl > 0 ? ps.ins.fmt(o.sl) : "—",
                    o.tp > 0 ? ps.ins.fmt(o.tp) : "—"
            });
        }

        histModel.setRowCount(0);
        List<TradeRecord> h = account.history;
        int from = Math.max(0, h.size() - 200);
        for (int i = h.size() - 1; i >= from; i--) {
            TradeRecord r = h.get(i);
            histModel.addRow(new Object[]{
                    engine.timeOf(r.minute).format(TF), r.symbol, r.action,
                    String.format("%.2f", r.lots), String.format("%.5f", r.price),
                    String.format("%+.2f", r.pnl), UI.money(r.balance)
            });
        }

        tabs.setTitleAt(0, "持仓 " + account.positions.size());
        tabs.setTitleAt(1, "挂单 " + account.pending.size());
        tabs.setTitleAt(2, "历史 " + account.history.size());

        if (engine.log.size() != lastLogCount) {
            lastLogCount = engine.log.size();
            StringBuilder sb = new StringBuilder();
            for (String s : engine.log) sb.append(s).append('\n');
            logArea.setText(sb.toString());
            logArea.setCaretPosition(0);
        }
    }
}
