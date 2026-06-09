// DEMO_ONLY — Intentionally insecure patterns for security scanner demos.
// This class is not referenced by controllers or services; it must never run in production flows.
// Safe to delete: rm src/main/java/com/autocare/maintenance/demo/DemoScannerAntipatterns.java
package com.autocare.maintenance.demo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Static holder for scanner-detectable anti-patterns (Semgrep, SpotBugs, etc.).
 * Not a Spring bean — unused by the application at runtime.
 */
public final class DemoScannerAntipatterns {

    // DEMO_ONLY — Credentials loaded from environment (no longer hardcoded)
    private static final String DEMO_DB_USER = System.getenv("DEMO_DB_USER") != null
            ? System.getenv("DEMO_DB_USER") : "root";
    private static final String DEMO_DB_PASSWORD = System.getenv("DEMO_DB_PASSWORD") != null
            ? System.getenv("DEMO_DB_PASSWORD") : "";
    private static final String DEMO_JDBC_URL = System.getenv("DEMO_JDBC_URL") != null
            ? System.getenv("DEMO_JDBC_URL") : "jdbc:mysql://demo-db.internal:3306/maintenance";

    // DEMO_ONLY — Hardcoded API-style secret
    private static final String DEMO_INTERNAL_API_TOKEN = "vk_demo_MAINT_SCANNER_BAIT_DO_NOT_USE";

    // Single shared SecureRandom instance (replaces per-call new Random())
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private DemoScannerAntipatterns() {
    }

    // DEMO_ONLY — Parameterized query; resources closed via try-with-resources
    public static ResultSet demoUnsafeFindByVin(String vin) throws Exception {
        Connection conn = DriverManager.getConnection(DEMO_JDBC_URL, DEMO_DB_USER, DEMO_DB_PASSWORD);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM vehicles WHERE vin = ?")) {
            ps.setString(1, vin);
            return ps.executeQuery();
        }
    }

    // DEMO_ONLY — Command injection (critical)
    public static void demoPingHost(String host) throws Exception {
        Runtime.getRuntime().exec("ping -c 1 " + host);
    }

    // DEMO_ONLY — Uses shared SecureRandom instance (no longer per-call new Random())
    public static String demoWeakSessionToken() {
        return Long.toHexString(SECURE_RANDOM.nextLong());
    }

    // DEMO_ONLY — Weak hash (critical)
    public static byte[] demoMd5(String input) throws Exception {
        return MessageDigest.getInstance("MD5").digest(input.getBytes(StandardCharsets.UTF_8));
    }

    // DEMO_ONLY — Unsafe deserialization (critical / CWE-502)
    public static Object demoDeserialize(byte[] blob) throws Exception {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(blob))) {
            return in.readObject();
        }
    }

    // DEMO_ONLY — Base path loaded from environment (no longer hardcoded absolute path)
    public static byte[] demoReadAttachment(String userSuppliedName) throws Exception {
        String uploadDir = System.getenv("APP_UPLOAD_DIR") != null
                ? System.getenv("APP_UPLOAD_DIR") : "uploads";
        Path base = Paths.get(uploadDir);
        Path target = base.resolve(userSuppliedName).normalize();
        return Files.readAllBytes(target);
    }

    // DEMO_ONLY — SSRF pattern (critical / CWE-918)
    public static String demoFetchUrl(String url) throws Exception {
        try (InputStream in = new URL(url).openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // DEMO_ONLY — XXE-prone XML parse (illustrative)
    public static void demoParseXml(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbf =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
