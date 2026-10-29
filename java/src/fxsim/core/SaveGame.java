package fxsim.core;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 存档：行情引擎 + 外汇账户 + 期货账户 + 玩家状态，写进 save/session.sav.gz。
 * 读档后行情会从原来的位置继续，而不是重新生成。
 */
public final class SaveGame {
    public static final Path FILE = Paths.get("save", "session.sav.gz");
    private static final String MAGIC = "FXSIM\t4";

    public record Session(MarketEngine engine, Account fx, Account fut, Player player) {
    }

    private SaveGame() {
    }

    public static boolean exists() {
        return Files.exists(FILE);
    }

    public static void save(MarketEngine engine, Account fx, Account fut, Player player) throws IOException {
        Files.createDirectories(FILE.getParent());
        Path tmp = FILE.resolveSibling("session.sav.tmp");
        try (PrintWriter p = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(tmp)), StandardCharsets.UTF_8)))) {
            p.println(MAGIC);
            engine.write(p);
            fx.write(p);
            fut.write(p);
            player.write(p);
            p.println("END");
            p.flush();
            if (p.checkError()) throw new IOException("写入存档失败");
        }
        Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Session load() throws IOException {
        MarketEngine engine = new MarketEngine();
        Account fx = new Account(engine);
        fx.asset = Instrument.Asset.FX;
        Account fut = new Account(engine);
        fut.asset = Instrument.Asset.FUT;
        fut.balance = 100_000;
        Player player = new Player(engine);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new BufferedInputStream(Files.newInputStream(FILE))), StandardCharsets.UTF_8))) {
            String magic = br.readLine();
            if (magic == null || !magic.startsWith("FXSIM")) return null;
            engine.read(br);
            fx.read(br);
            fut.read(br);
            player.read(br);
        }
        return new Session(engine, fx, fut, player);
    }

    public static Session loadQuietly() {
        if (!exists()) return null;
        try {
            return load();
        } catch (Exception ex) {
            System.err.println("读档失败，改为新开局：" + ex);
            return null;
        }
    }

    public static void saveQuietly(MarketEngine engine, Account fx, Account fut, Player player) {
        try {
            save(engine, fx, fut, player);
        } catch (Exception ex) {
            System.err.println("存档失败：" + ex);
        }
    }

    public static void delete() {
        try {
            Files.deleteIfExists(FILE);
        } catch (IOException ignored) {
        }
    }
}
