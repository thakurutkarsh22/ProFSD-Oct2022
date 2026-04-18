package com.demo.kafka;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * Right-click → Run in IntelliJ to start/stop Kafka + Kafka UI.
 *
 * Usage:
 *   No args  → starts Kafka + Kafka UI, opens browser to localhost:8080
 *   "stop"   → stops everything (docker compose down)
 *   "status" → shows running containers
 */
public class KafkaEnvironment {

    private static final String KAFKA_UI_URL = "http://localhost:8080";
    private static final int MAX_WAIT_SECONDS = 90;

    public static void main(String[] args) throws Exception {
        String action = args.length > 0 ? args[0].toLowerCase() : "start";

        switch (action) {
            case "stop", "down" -> stop();
            case "status" -> status();
            default -> start();
        }
    }

    private static void start() throws Exception {
        printBanner("STARTING KAFKA ENVIRONMENT");

        File composeDir = findComposeDir();
        System.out.println("  Docker Compose dir: " + composeDir.getAbsolutePath());

        // Check if already running
        if (isKafkaUIReachable()) {
            System.out.println("\n  ✓ Kafka UI is already running at " + KAFKA_UI_URL);
            openBrowser();
            return;
        }

        // Start containers
        System.out.println("\n  Starting Kafka (KRaft) + Kafka UI via Docker Compose...\n");
        int exitCode = runCommand(composeDir, "docker", "compose", "up", "-d");

        if (exitCode != 0) {
            System.err.println("\n  ✗ docker compose up failed (exit code " + exitCode + ")");
            System.err.println("    Make sure Docker Desktop is running.");
            System.exit(1);
        }

        // Wait for Kafka broker to be healthy
        System.out.println("\n  Waiting for Kafka broker to become healthy...");
        waitForKafkaBroker(composeDir);

        // Wait for Kafka UI to respond
        System.out.println("  Waiting for Kafka UI at " + KAFKA_UI_URL + "...");
        waitForKafkaUI();

        System.out.println("\n  ✓ Kafka broker is running on localhost:9092");
        System.out.println("  ✓ Kafka UI is running on " + KAFKA_UI_URL);

        openBrowser();

        printBanner("ENVIRONMENT READY");
        System.out.println("""
                  Now you can:
                    1. Browse topics, messages, consumers at http://localhost:8080
                    2. Run KafkaPlayground.main() for the interactive CLI
                    3. Run KafkaEnvironment.main("stop") to tear down
                
                  ┌─────────────────────────────────────────────────────────┐
                  │  Kafka Broker     → localhost:9092                      │
                  │  Kafka UI         → http://localhost:8080               │
                  │  Docker Compose   → kafka-demo + kafka-ui containers   │
                  └─────────────────────────────────────────────────────────┘
                """);
    }

    private static void stop() throws Exception {
        printBanner("STOPPING KAFKA ENVIRONMENT");
        File composeDir = findComposeDir();
        System.out.println("  Running: docker compose down\n");
        runCommand(composeDir, "docker", "compose", "down");
        System.out.println("\n  ✓ Kafka and Kafka UI stopped.");
        System.out.println("    To also remove data volumes: docker compose down -v");
    }

    private static void status() throws Exception {
        printBanner("KAFKA ENVIRONMENT STATUS");
        File composeDir = findComposeDir();
        runCommand(composeDir, "docker", "compose", "ps");

        System.out.println();
        if (isKafkaUIReachable()) {
            System.out.println("  ✓ Kafka UI is reachable at " + KAFKA_UI_URL);
        } else {
            System.out.println("  ✗ Kafka UI is NOT reachable. Run this class to start it.");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private static File findComposeDir() {
        // Try multiple strategies to find docker-compose.yml
        String[] candidates = {
                System.getProperty("user.dir"),
                System.getProperty("user.dir") + "/src/HLD/Components/examples/kafka-java-demo",
        };

        for (String candidate : candidates) {
            File dir = new File(candidate);
            if (new File(dir, "docker-compose.yml").exists()) {
                return dir;
            }
        }

        // Walk up from the class location
        String classPath = KafkaEnvironment.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        File dir = new File(classPath);
        for (int i = 0; i < 10; i++) {
            dir = dir.getParentFile();
            if (dir == null) break;
            if (new File(dir, "docker-compose.yml").exists()) return dir;
        }

        System.err.println("  ✗ Cannot find docker-compose.yml.");
        System.err.println("    Run from the kafka-java-demo directory or set working directory in IntelliJ.");
        System.exit(1);
        return null;
    }

    private static int runCommand(File workDir, String... cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(workDir)
                .inheritIO();
        Process process = pb.start();
        return process.waitFor();
    }

    private static void waitForKafkaBroker(File composeDir) throws Exception {
        long deadline = System.currentTimeMillis() + MAX_WAIT_SECONDS * 1000L;

        while (System.currentTimeMillis() < deadline) {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "inspect", "--format", "{{.State.Health.Status}}", "kafka-demo")
                    .directory(composeDir)
                    .redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();

            if ("healthy".equals(output)) {
                System.out.println("  ✓ Kafka broker is healthy.");
                return;
            }

            System.out.print("  .");
            Thread.sleep(3000);
        }

        System.err.println("\n  ✗ Kafka broker did not become healthy within " + MAX_WAIT_SECONDS + "s");
        System.exit(1);
    }

    private static void waitForKafkaUI() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 60_000;

        while (System.currentTimeMillis() < deadline) {
            if (isKafkaUIReachable()) {
                System.out.println("  ✓ Kafka UI is ready.");
                return;
            }
            System.out.print("  .");
            Thread.sleep(2000);
        }

        System.out.println("\n  ⚠ Kafka UI not responding yet — it may still be starting.");
        System.out.println("    Try opening " + KAFKA_UI_URL + " manually in a minute.");
    }

    private static boolean isKafkaUIReachable() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(KAFKA_UI_URL).openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private static void openBrowser() {
        System.out.println("\n  Opening Kafka UI in browser...");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(KAFKA_UI_URL));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", KAFKA_UI_URL});
                } else if (os.contains("linux")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", KAFKA_UI_URL});
                } else if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", KAFKA_UI_URL});
                }
            }
        } catch (Exception e) {
            System.out.println("  Could not auto-open browser. Open manually: " + KAFKA_UI_URL);
        }
    }

    private static void printBanner(String title) {
        String line = "═".repeat(title.length() + 6);
        System.out.println("\n  ╔" + line + "╗");
        System.out.println("  ║   " + title + "   ║");
        System.out.println("  ╚" + line + "╝\n");
    }
}
