package com.sharktower.bloodonthesharktower.serverupdate;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Plain Java regression executable: pack.toml parsing, duplicate-mod clean-up and the website link. */
public final class ServerUpdateRegression {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        PackInfo pack = PackInfo.parse("""
                name = "Blood on the Sharktower"
                version = "1.0.2-alpha.48"
                pack-format = "packwiz:1.1.0"

                [index]
                file = "index.toml"
                hash-format = "sha256"
                hash = "abc"

                [versions]
                minecraft = "26.3"
                fabric = "0.19.5"
                """);
        check(pack.version().equals("1.0.2-alpha.48"), "pack version read from the top level");
        check(pack.minecraft().equals("26.3"), "minecraft read from [versions]");
        check(pack.fabric().equals("0.19.5"), "fabric read from [versions]");
        boolean refused = false;
        try {
            PackInfo.parse("name = \"x\"\n");
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(refused, "pack.toml without versions is refused");

        Path dir = Files.createTempDirectory("sharktower-dedupe");
        try {
            Path mods = Files.createDirectories(dir.resolve("mods"));
            jar(mods.resolve("blood-on-the-sharktower-1.0.2-alpha.48.jar"), "blood_on_the_sharktower");
            jar(mods.resolve("blood-on-the-sharktower-1.0.1.jar"), "blood_on_the_sharktower");
            jar(mods.resolve("fabric-api-0.161.0.jar"), "fabric-api");
            jar(mods.resolve("luckperms.jar"), "luckperms");
            Files.writeString(dir.resolve("packwiz.json"), """
                    {"cachedFiles": {
                      "mods/blood-on-the-sharktower.pw.toml": {"cachedLocation": "mods/blood-on-the-sharktower-1.0.2-alpha.48.jar"},
                      "mods/fabric-api.pw.toml": {"cachedLocation": "mods/fabric-api-0.161.0.jar"}
                    }}
                    """);
            List<String> moved = ModDedupe.run(dir, dir.resolve("old-mods"));
            check(moved.equals(List.of("blood-on-the-sharktower-1.0.1.jar")), "only the old duplicate is moved: " + moved);
            check(Files.exists(dir.resolve("old-mods/blood-on-the-sharktower-1.0.1.jar")), "old JAR kept as a backup");
            check(Files.exists(mods.resolve("luckperms.jar")), "server-only extra mod left alone");
            check(Files.exists(mods.resolve("blood-on-the-sharktower-1.0.2-alpha.48.jar")), "tracked JAR kept");
            check(ModDedupe.run(dir, dir.resolve("old-mods")).isEmpty(), "second run moves nothing");
        } finally {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }

        siteLinkSendsNoUpgrade();

        System.out.println("ServerUpdateRegression passed (" + checks + " checks).");
    }

    /** The website link speaks plain HTTP/1.1, so it never asks the site to upgrade the connection. */
    private static void siteLinkSendsNoUpgrade() throws Exception {
        List<String> upgrades = new CopyOnWriteArrayList<>();
        HttpServer site = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        site.createContext("/internal/minecraft/live", exchange -> {
            String upgrade = exchange.getRequestHeaders().getFirst("Upgrade");
            upgrades.add(upgrade == null ? "none" : upgrade);
            exchange.getRequestBody().readAllBytes();
            byte[] reply = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        site.start();
        try {
            setSiteLink("url", "http://127.0.0.1:" + site.getAddress().getPort());
            setSiteLink("key", "k".repeat(32));
            List<String> replies = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 2; i++) {
                SiteLink.post("/internal/minecraft/live", "{}", "spectator updates", replies::add)
                        .get(10, TimeUnit.SECONDS);
            }
            check(upgrades.equals(List.of("none", "none")), "website link asked to upgrade: " + upgrades);
            check(replies.equals(List.of("{}", "{}")), "site replies reach the caller: " + replies);
        } finally {
            site.stop(0);
        }
    }

    private static void setSiteLink(String field, String value) throws ReflectiveOperationException {
        Field f = SiteLink.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void jar(Path path, String modId) throws IOException {
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write(("{\"id\": \"" + modId + "\"}").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
