package fxsim.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * 启动前的风险提示页。必须点"我已阅读并知晓"才能进游戏。
 * 文案按合规要求写死，不做可配置。
 */
public final class DisclaimerDialog extends JDialog {
    private static final String[] LINES = {
            "根据中国相关法律法规",
            "目前境内未批准任何机构开展或代理开展外汇保证金交易",
            "本游戏所模拟的高杠杆、境外外汇经纪商等机制，在中国大陆境内属于违法违规行为",
            "参与任何境外未经批准的非法外汇平台交易",
            "不受中国法律保护，且面临极高的资金损失和网络诈骗风险",
            "请广大玩家认清非法外汇交易的本质，遵守国家外汇管理规定",
            "现实中请通过国家批准的合法金融机构（如各大银行）办理合规的外汇业务",
    };

    private static final String RED_LINE = "投资有风险，入市需谨慎。";

    private static final String[] TAIL = {
            "过度交易可能对您的财务和日常生活造成严重影响，请坚决抵制任何形式的非法炒汇平台",
            "因参与非法外汇交易导致的任何损失，需由参与者自行承担",
    };

    private boolean accepted;

    public DisclaimerDialog() {
        super((Frame) null, "风险提示", true);
        setUndecorated(true);
        setSize(980, 640);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        Theme t = Theme.I;
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(t.bg);
        root.add(new Body(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 18));
        bottom.setOpaque(false);
        FlatButton ok = new FlatButton("我已阅读并知晓，进入游戏", () -> Theme.I.accent, () -> Color.WHITE, () -> {
            accepted = true;
            dispose();
        });
        ok.setPreferredSize(new Dimension(280, 40));
        FlatButton quit = new FlatButton("退出", () -> Theme.I.panelAlt, () -> Theme.I.muted, () -> {
            accepted = false;
            dispose();
        });
        quit.setPreferredSize(new Dimension(90, 40));
        bottom.add(ok);
        bottom.add(quit);
        root.add(bottom, BorderLayout.SOUTH);

        root.setBorder(BorderFactory.createLineBorder(Theme.mix(t.border, t.accent2, 0.35), 1));
        setContentPane(root);

        getRootPane().registerKeyboardAction(e -> {
            accepted = true;
            dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> {
            accepted = false;
            dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public boolean accepted() {
        return accepted;
    }

    /** 正文：三角警示 + 标题 + 合规文案，全部自绘，保证居中 */
    private static final class Body extends JComponent {
        @Override
        protected void paintComponent(Graphics g0) {
            Theme t = Theme.I;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth();
            int cx = w / 2;

            g.setColor(t.bg);
            g.fillRect(0, 0, w, getHeight());

            // 顶部两侧装饰线
            g.setColor(Theme.alpha(t.accent2, 180));
            g.fillRect(cx - 430, 40, 250, 2);
            g.fillRect(cx + 180, 40, 250, 2);

            // 警示三角
            int tw = 66, ty = 22;
            int[] xs = {cx, cx - tw / 2, cx + tw / 2};
            int[] ys = {ty, ty + 58, ty + 58};
            g.setColor(Theme.alpha(t.accent2, 40));
            g.fillPolygon(xs, ys, 3);
            g.setColor(t.accent2);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolygon(xs, ys, 3);
            g.fillRoundRect(cx - 2, ty + 18, 4, 22, 3, 3);
            g.fillOval(cx - 3, ty + 45, 6, 6);
            g.setStroke(new BasicStroke(1f));

            // 标题
            g.setFont(t.font(26, true));
            g.setColor(t.text);
            drawCenter(g, "本游戏纯属虚构", cx, 138);

            // 正文
            int y = 186;
            g.setFont(t.font(14, false));
            g.setColor(Theme.mix(t.text, t.muted, 0.25));
            for (String s : LINES) {
                drawCenter(g, s, cx, y);
                y += 30;
            }

            g.setFont(t.font(15, true));
            g.setColor(Color.decode("#E05555"));
            drawCenter(g, RED_LINE, cx, y + 4);
            y += 38;

            g.setFont(t.font(14, false));
            g.setColor(Theme.mix(t.text, t.muted, 0.25));
            for (String s : TAIL) {
                drawCenter(g, s, cx, y);
                y += 30;
            }

            // 脚注
            g.setFont(t.font(11, false));
            g.setColor(t.muted);
            drawCenter(g, "本程序完全离线运行，不接入任何真实账户，不涉及任何真实资金", cx, y + 12);

            g.dispose();
        }

        private void drawCenter(Graphics2D g, String s, int cx, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, cx - fm.stringWidth(s) / 2f, y);
        }
    }
}
