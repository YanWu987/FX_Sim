package fxsim;

import fxsim.ui.DisclaimerDialog;
import fxsim.ui.MainFrame;
import fxsim.ui.Theme;
import fxsim.ui.UI;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 程序入口。支持 --demo（预置持仓）与 --shot <png>（离屏截图，用于自检）。 */
public final class Main {
    public static void main(String[] args) {
        Theme.I.load();
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        UI.applyUI();

        boolean firstRun = !fxsim.core.SaveGame.exists();
        String shot = arg(args, "--shot");
        boolean demo = flag(args, "--demo") || shot != null;

        SwingUtilities.invokeLater(() -> {
            // 启动前先过风险提示；截图模式跳过，方便自动出图
            if (shot == null) {
                DisclaimerDialog tip = new DisclaimerDialog();
                tip.setVisible(true);
                if (!tip.accepted()) {
                    System.exit(0);
                    return;
                }
            }
            MainFrame f = new MainFrame(demo);
            if (flag(args, "--fut")) f.showMarket(fxsim.core.Instrument.Asset.FUT);
            f.setLocation(0, 0);
            f.setVisible(true);
            if (firstRun && shot == null) {
                SwingUtilities.invokeLater(f::openTutorial);
            }
            if (shot != null) {
                Timer tm = new Timer(1200, e -> {
                    save(f, shot);
                    System.exit(0);
                });
                tm.setRepeats(false);
                tm.start();
            }
        });
    }

    private static void save(JFrame f, String path) {
        try {
            Container c = f.getContentPane();
            BufferedImage img = new BufferedImage(Math.max(1, c.getWidth()), Math.max(1, c.getHeight()),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            c.printAll(g);
            g.dispose();
            File out = new File(path);
            ImageIO.write(img, "png", out);
            System.out.println("saved: " + out.getAbsolutePath() + "  " + img.getWidth() + "x" + img.getHeight());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static String arg(String[] args, String key) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) return args[i + 1];
        }
        return null;
    }

    private static boolean flag(String[] args, String key) {
        for (String a : args) if (a.equals(key)) return true;
        return false;
    }
}
