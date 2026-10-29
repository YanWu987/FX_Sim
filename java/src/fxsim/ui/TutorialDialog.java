package fxsim.ui;

import fxsim.core.Account;
import fxsim.core.MarketEngine;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 教程：一张悬浮卡片 + 高亮对应面板，关键步骤会等你真的做出来才继续。
 */
public final class TutorialDialog extends JDialog {
    private record Step(String title, String body, String hint,
                        Supplier<JComponent> target, BooleanSupplier done) {
    }

    private final MarketEngine engine;
    private final Account account;
    private final ChartPanel chart;
    private final WatchlistPanel watch;
    private final TicketPanel ticket;
    private final OrdersPanel orders;
    private final HeaderPanel header;

    private final List<Step> steps = new ArrayList<>();
    private int index = -1;
    private JComponent highlighted;
    private Border oldBorder;

    private final JLabel stepLabel = UI.label("", 9, Theme.I.muted, false);
    private final JLabel titleLabel = UI.label("", 14, Theme.I.text, true);
    private final JLabel bodyLabel = UI.label("", 11, Theme.I.muted, false);
    private final JLabel hintLabel = UI.label("", 10, Theme.I.accent2, true);
    private final FlatButton prevBtn = new FlatButton("上一步", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::prev);
    private final FlatButton nextBtn = new FlatButton("下一步", new Color(0x4C, 0x8D, 0xFF), Color.WHITE, this::next);
    private final Timer timer;

    public TutorialDialog(MainFrame owner, MarketEngine engine, Account account,
                          ChartPanel chart, WatchlistPanel watch, TicketPanel ticket,
                          OrdersPanel orders, HeaderPanel header) {
        super(owner, "教程", false);
        this.engine = engine;
        this.account = account;
        this.chart = chart;
        this.watch = watch;
        this.ticket = ticket;
        this.orders = orders;
        this.header = header;

        setUndecorated(true);
        setSize(400, 300);
        setAlwaysOnTop(true);

        Theme t = Theme.I;
        JPanel root = new JPanel();
        root.setBackground(t.panel);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.mix(t.accent, t.border, 0.4), 2),
                new EmptyBorder(12, 16, 12, 16)));

        stepLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(stepLabel);
        titleLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(titleLabel);
        root.add(Box.createVerticalStrut(8));

        bodyLabel.setAlignmentX(LEFT_ALIGNMENT);
        bodyLabel.setVerticalAlignment(SwingConstants.TOP);
        root.add(bodyLabel);

        root.add(Box.createVerticalGlue());
        hintLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(hintLabel);
        root.add(Box.createVerticalStrut(8));

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btns.setOpaque(false);
        btns.setAlignmentX(LEFT_ALIGNMENT);
        btns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        FlatButton skip = new FlatButton("跳过教程", () -> Theme.I.panelAlt, () -> Theme.I.muted, this::dispose);
        prevBtn.setPreferredSize(new Dimension(72, 28));
        nextBtn.setPreferredSize(new Dimension(72, 28));
        skip.setPreferredSize(new Dimension(88, 28));
        btns.add(prevBtn);
        btns.add(nextBtn);
        btns.add(skip);
        root.add(btns);

        setContentPane(root);
        buildSteps();
        showStep(0);

        timer = new Timer(400, e -> {
            if (index < 0 || index >= steps.size()) return;
            BooleanSupplier done = steps.get(index).done();
            if (done != null && done.getAsBoolean()) next();
        });
        timer.start();
    }

    // ------------------------------------------------------------------
    private void buildSteps() {
        steps.add(new Step("欢迎来到 FX Sim",
                "<html><body style='width:340px'>左边是自选列表，中间是 K 线，左下是下单面板，底部是持仓。<br><br>"
                        + "先花 30 秒熟悉一下布局，这个窗口会一直跟着你。</body></html>",
                "看完点「下一步」", () -> watch, null));

        steps.add(new Step("看懂报价",
                "<html><body style='width:320px'>每一行有两个价格：<b>买价</b>（你卖出时成交）和<b>卖价</b>（你买入时成交）。<br><br>"
                        + "两者的差就是<b>点差</b>——交易成本。EUR/USD 约 1 个点。</body></html>",
                "点一下 EUR/USD 试试", () -> watch, null));

        steps.add(new Step("K 线怎么读",
                "<html><body style='width:320px'>一根蜡烛 = 一个周期内的开、高、低、收。绿色是收涨、红色是收跌。<br><br>"
                        + "滚轮缩放、按住拖动平移；右上角 1分 / 1时 / 日线 换周期。</body></html>",
                "试试滚轮和拖动", () -> chart, null));

        steps.add(new Step("下第一单",
                "<html><body style='width:320px'>左下角：手数保持 <b>0.10</b>，类型「市价」，然后点<b>「买入 BUY」</b>。<br><br>"
                        + "0.1 手 EUR/USD，每个点大约 1 美元。</body></html>",
                "等你下单…", () -> ticket, () -> !account.positions.isEmpty()));

        steps.add(new Step("持仓与浮动盈亏",
                "<html><body style='width:320px'>底部「持仓」页会出现你刚才的单子：开仓价、现价、点数、浮动盈亏。<br><br>"
                        + "浮动盈亏会随价格实时跳动，但只有平仓才真正进余额。</body></html>",
                "看一眼那张单子", () -> orders, null));

        steps.add(new Step("平掉它",
                "<html><body style='width:320px'>在持仓行上<b>双击</b>，或者选中后点「平仓选中」。<br><br>"
                        + "平仓后，盈亏才会结算进余额。</body></html>",
                "等你平仓…", () -> orders, () -> account.positions.isEmpty()));

        steps.add(new Step("保证金与强平",
                "<html><body style='width:320px'>顶部一排：余额、净值、已用保证金、可用、保证金水平。<br><br>"
                        + "保证金水平低于 50% 会被强制平仓——这是最容易一夜归零的地方。</body></html>",
                "记住 50% 这条线", () -> header, null));

        steps.add(new Step("别忘了活着",
                "<html><body style='width:320px'>右上角「生活」：吃饭、喝水、睡觉、借钱。<br><br>"
                        + "生命归零会被送医院，住院两天，医药费照扣。<br>"
                        + "困了喝咖啡，但一天别超过 5 杯。</body></html>",
                "点开「生活」看一眼", () -> header, null));

        steps.add(new Step("教程结束",
                "<html><body style='width:320px'>工具栏可以切到<b>期货</b>（独立账户、逐日盯市、涨跌停）。<br><br>"
                        + "想折腾数值：按 <b>Ctrl+D</b> 打开调试台，能改行情、注入走势、快进时间。<br><br>"
                        + "祝你好运——记住，杠杆是双向的。</body></html>",
                "", null, null));
    }

    private void showStep(int i) {
        if (i < 0 || i >= steps.size()) {
            dispose();
            return;
        }
        index = i;
        Step s = steps.get(i);
        stepLabel.setText(String.format("教程 %d / %d", i + 1, steps.size()));
        titleLabel.setText(s.title());
        bodyLabel.setText(s.body());
        hintLabel.setText(s.hint());
        prevBtn.setEnabled(i > 0);
        nextBtn.setText(i == steps.size() - 1 ? "完成" : "下一步");

        restoreHighlight();
        JComponent target = s.target() == null ? null : s.target().get();
        if (target != null) {
            highlighted = target;
            oldBorder = target.getBorder();
            target.setBorder(BorderFactory.createLineBorder(Theme.I.accent, 2));
        }
        placeCard(target);
        revalidate();
        repaint();
    }

    private void placeCard(JComponent target) {
        try {
            Point base = getOwner().getLocationOnScreen();
            int x = base.x + getOwner().getWidth() - getWidth() - 24;
            int y = base.y + getOwner().getHeight() - getHeight() - 56;
            if (target != null) {
                Point p = target.getLocationOnScreen();
                // 卡片别盖住高亮区域：目标的左侧优先
                if (p.x > getWidth() + 60) {
                    x = p.x - getWidth() - 16;
                    y = Math.max(base.y + 90, Math.min(y, p.y));
                }
            }
            setLocation(x, y);
        } catch (IllegalComponentStateException ignored) {
            setLocationRelativeTo(getOwner());
        }
    }

    private void restoreHighlight() {
        if (highlighted != null) {
            highlighted.setBorder(oldBorder);
            highlighted = null;
        }
    }

    private void next() {
        if (index + 1 >= steps.size()) {
            dispose();
            return;
        }
        showStep(index + 1);
    }

    private void prev() {
        if (index > 0) showStep(index - 1);
    }

    @Override
    public void dispose() {
        restoreHighlight();
        if (timer != null) timer.stop();
        super.dispose();
    }
}
