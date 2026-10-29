package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.Instrument;
import fxsim.core.MarketEngine;
import fxsim.core.Player;
import fxsim.core.SaveGame;
import fxsim.core.Side;
import fxsim.core.Timeframe;

import java.util.List;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** 主窗口 */
public final class MainFrame extends JFrame {
    private final MarketEngine engine;
    private Account account;
    private final Account fxAccount;
    private final Account futAccount;
    private final Player player;
    private final boolean demoMode;

    private final ChartPanel chart;
    private final WatchlistPanel watch;
    private final TicketPanel ticket;
    private final OrdersPanel orders;
    private final HeaderPanel header;
    private final StatusBar status;
    private LifeDialog life;

    private final JLabel symbolLabel = UI.label("EUR/USD", 14, Theme.I.text, true);
    private final JLabel quoteLabel = UI.label("", 11, Theme.I.muted, false);
    private final Chip[] tfChips = new Chip[Timeframe.values().length];
    private SettingsDialog settings;
    private DebugDialog debug;
    private TutorialDialog tutorial;
    private Chip[] marketChips;

    private long lastNanos = System.nanoTime();
    private long lastTable = 0;
    private long lastSave = System.nanoTime();
    private int frame;

    public MainFrame(boolean demo) {
        super("FX SIM · 外汇模拟交易终端");
        Theme t = Theme.I;
        demoMode = demo;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 730));
        setSize(1580, 950);
        setLocationRelativeTo(null);
        getContentPane().setBackground(t.bg);
        setLayout(new BorderLayout());

        SaveGame.Session loaded = demo ? null : SaveGame.loadQuietly();
        if (loaded != null) {
            engine = loaded.engine();
            fxAccount = loaded.fx();
            futAccount = loaded.fut();
            player = loaded.player();
        } else {
            engine = new MarketEngine();
            fxAccount = new Account(engine);
            futAccount = new Account(engine);
            futAccount.asset = Instrument.Asset.FUT;
            futAccount.balance = 100_000;
            player = new Player(engine);
        }
        account = fxAccount;
        engine.onNewDay = () -> {
            fxAccount.onNewDay();
            futAccount.onNewDay();
        };
        if (demo) demo();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!demoMode) SaveGame.saveQuietly(engine, fxAccount, futAccount, player);
            }
        });

        chart = new ChartPanel(engine, account);
        watch = new WatchlistPanel(engine);
        watch.setSymbols(engine.symbols(Instrument.Asset.FX));
        ticket = new TicketPanel(engine, account);
        orders = new OrdersPanel(engine, account, chart::symbol);
        status = new StatusBar(engine, player);
        header = new HeaderPanel(engine, account, player, this::openSettings, this::resetAccount, this::openLife, this::openTutorial);

        watch.onSelect(s -> {
            chart.setSymbol(s);
            ticket.setSymbol(s);
            symbolLabel.setText(s);
        });

        // ---------------- 布局 ----------------
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setBackground(t.panel);
        toolbar.setPreferredSize(new Dimension(0, 46));
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, t.border));

        JPanel tl = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 13));
        tl.setOpaque(false);
        marketChips = new Chip[]{
                new Chip("外汇", true, () -> switchMarket(Instrument.Asset.FX)),
                new Chip("期货", false, () -> switchMarket(Instrument.Asset.FUT))};
        tl.add(marketChips[0]);
        tl.add(marketChips[1]);
        tl.add(javax.swing.Box.createHorizontalStrut(6));
        tl.add(symbolLabel);
        tl.add(quoteLabel);
        toolbar.add(tl, BorderLayout.WEST);

        JPanel tr = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 9));
        tr.setOpaque(false);
        Timeframe[] tfs = Timeframe.values();
        for (int i = 0; i < tfs.length; i++) {
            final Timeframe tf = tfs[i];
            tfChips[i] = new Chip(tf.label, tf == chart.timeframe(), () -> {
                chart.setTimeframe(tf);
                syncTf();
            });
            tr.add(tfChips[i]);
        }
        toolbar.add(tr, BorderLayout.EAST);

        JPanel leftCol = new JPanel(new BorderLayout());
        leftCol.setBackground(t.panel);
        leftCol.setPreferredSize(new Dimension(320, 0));
        JScrollPane watchScroll = UI.scroll(watch);
        watchScroll.setPreferredSize(new Dimension(318, Math.min(470, watch.getPreferredSize().height)));
        watchScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftCol.add(watchScroll, BorderLayout.NORTH);

        JScrollPane ticketScroll = UI.scroll(ticket);
        ticketScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftCol.add(ticketScroll, BorderLayout.CENTER);

        JPanel rightCol = new JPanel(new BorderLayout());
        rightCol.setBackground(t.bg);
        rightCol.add(toolbar, BorderLayout.NORTH);

        JSplitPane vs = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chart, orders);
        vs.setResizeWeight(0.66);
        vs.setDividerLocation(545);
        vs.setBorder(null);
        vs.setContinuousLayout(true);
        rightCol.add(vs, BorderLayout.CENTER);

        JSplitPane hs = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftCol, rightCol);
        hs.setDividerLocation(320);
        hs.setResizeWeight(0);
        hs.setBorder(null);
        hs.setContinuousLayout(true);

        add(header, BorderLayout.NORTH);
        add(hs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        bindKeys();
        Theme.I.onChanged(() -> SwingUtilities.invokeLater(() -> {
            UI.applyUI();
            SwingUtilities.updateComponentTreeUI(this);
            UI.restyle(this);
            quoteLabel.setForeground(Theme.I.muted);
            repaint();
        }));

        Timer timer = new Timer(16, e -> onFrame());
        timer.start();
        orders.refresh();
    }

    // ------------------------------------------------------------------
    private void demo() {
        engine.speed = 60;
        for (int i = 0; i < 260; i++) {
            engine.tick(1.0);
            account.update();
        }
        demoOpen("EUR/USD", Side.BUY, 0.50, 55, 110);
        demoOpen("USD/JPY", Side.SELL, 0.30, 50, 100);
        demoOpen("XAU/USD", Side.BUY, 0.20, 150, 320);
        account.placePending("GBP/USD", Side.BUY, 0.20,
                fxsim.core.PendingOrder.Type.LIMIT, engine.pair("GBP/USD").mid * 0.99);
        for (int i = 0; i < 12; i++) {
            engine.tick(1.0);
            account.update();
        }
        engine.setGear(1);
    }

    private void demoOpen(String symbol, Side side, double lots, double slPips, double tpPips) {
        var ps = engine.pair(symbol);
        double entry = side == Side.BUY ? ps.ask() : ps.bid();
        account.openMarket(symbol, side, lots,
                entry - side.dir * slPips * ps.ins.pipSize,
                entry + side.dir * tpPips * ps.ins.pipSize);
    }

    private void onFrame() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;

        engine.tick(dt);
        account.update();
        player.update(fxAccount);
        player.settleSkips(fxAccount);

        chart.repaint();
        watch.repaint();
        header.refresh();
        status.refresh();
        if ((frame & 3) == 0) {
            ticket.refresh();
            String sym = chart.symbol();
            var ps = engine.pair(sym);
            quoteLabel.setText(String.format("%s / %s   ·   点差 %.1f    ·   %s",
                    ps.ins.fmt(ps.bid()), ps.ins.fmt(ps.ask()), ps.spreadPips,
                    engine.paused ? "已暂停" : engine.marketOpen() ? "运行中" : "休市"));
        }
        if (now - lastTable > 180_000_000L) {
            lastTable = now;
            orders.refresh();
        }
        // 每 3 分钟自动存档一次，关窗口时还会再存一次
        if (!demoMode && now - lastSave > 180_000_000_000L) {
            lastSave = now;
            SaveGame.saveQuietly(engine, fxAccount, futAccount, player);
        }
        frame++;
    }

    private void syncTf() {
        Timeframe cur = chart.timeframe();
        Timeframe[] tfs = Timeframe.values();
        for (int i = 0; i < tfs.length; i++) tfChips[i].setSelected(tfs[i] == cur);
    }

    private void openSettings() {
        if (settings == null) settings = new SettingsDialog(this, engine);
        settings.setVisible(true);
    }

    /** 供截图/测试直接切市场 */
    public void showMarket(Instrument.Asset a) {
        switchMarket(a);
    }

    private void switchMarket(Instrument.Asset a) {
        if (marketChips != null) {
            marketChips[0].setSelected(a == Instrument.Asset.FX);
            marketChips[1].setSelected(a == Instrument.Asset.FUT);
        }
        account = a == Instrument.Asset.FX ? fxAccount : futAccount;
        List<String> syms = engine.symbols(a);
        watch.setSymbols(syms);
        chart.setAccount(account);
        ticket.setAccount(account);
        orders.setAccount(account);
        header.setAccount(account);
        String cur = chart.symbol();
        if (!syms.contains(cur) && !syms.isEmpty()) {
            chart.setSymbol(syms.get(0));
            ticket.setSymbol(syms.get(0));
            symbolLabel.setText(syms.get(0));
        }
        orders.refresh();
        header.refresh();
    }

    /** 打开新手教程 */
    public void openTutorial() {
        if (tutorial == null || !tutorial.isDisplayable()) {
            tutorial = new TutorialDialog(this, engine, fxAccount, chart, watch, ticket, orders, header);
        }
        tutorial.setVisible(true);
        tutorial.toFront();
    }

    private void openLife() {
        if (life == null || !life.isDisplayable()) life = new LifeDialog(this, engine, fxAccount, player);
        life.setVisible(true);
        life.toFront();
    }

    private void openDebug() {
        if (debug == null || !debug.isDisplayable()) debug = new DebugDialog(this, engine, account, player);
        debug.setVisible(true);
        debug.toFront();
    }

    private void resetAccount() {
        int r = JOptionPane.showConfirmDialog(this,
                "确认把账户重置为初始 10,000 USD？\n存档会被新的账户覆盖，行情从当前时间继续。",
                "重置账户", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.OK_OPTION) {
            fxAccount.reset();
            futAccount.reset();
            account = fxAccount;
            player.reset();
            SaveGame.delete();
            if (!demoMode) SaveGame.saveQuietly(engine, fxAccount, futAccount, player);
            orders.refresh();
        }
    }

    private void bindKeys() {
        JRootPane rp = getRootPane();
        InputMap im = rp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rp.getActionMap();
        im.put(KeyStroke.getKeyStroke("SPACE"), "pause");
        am.put("pause", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                status.togglePause();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "gearUp");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), "gearUp2");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), "gearDown");
        Action gearUp = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                status.stepGear(1);
            }
        };
        Action gearDown = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                status.stepGear(-1);
            }
        };
        am.put("gearUp", gearUp);
        am.put("gearUp2", gearUp);
        am.put("gearDown", gearDown);

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_D, KeyEvent.CTRL_DOWN_MASK), "debug");
        am.put("debug", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openDebug();
            }
        });
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0), "debug2");
        am.put("debug2", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openDebug();
            }
        });
        Timeframe[] tfs = Timeframe.values();
        for (int i = 0; i < tfs.length; i++) {
            final Timeframe tf = tfs[i];
            String key = "tf" + i;
            im.put(KeyStroke.getKeyStroke(KeyEvent.VK_1 + i, 0), key);
            am.put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    chart.setTimeframe(tf);
                    syncTf();
                }
            });
        }
    }

    @Override
    public void dispose() {
        Theme.I.save();
        super.dispose();
        System.exit(0);
    }
}
