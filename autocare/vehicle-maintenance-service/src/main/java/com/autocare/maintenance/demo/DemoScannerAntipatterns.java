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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;

/**
 * Static holder for scanner-detectable anti-patterns (Semgrep, SpotBugs, etc.).
 * Not a Spring bean — unused by the application at runtime.
 */
public final class DemoScannerAntipatterns {

    // DEMO_ONLY — Hardcoded credentials (secret scanners / Semgrep)
    private static final String DEMO_DB_USER = "root";
    private static final String DEMO_DB_PASSWORD = "VehicleMaintDemo123!";
    private static final String DEMO_JDBC_URL = "jdbc:mysql://demo-db.internal:3306/maintenance";

    // DEMO_ONLY — Hardcoded API-style secret
    private static final String DEMO_INTERNAL_API_TOKEN = "vk_demo_MAINT_SCANNER_BAIT_DO_NOT_USE";

    private DemoScannerAntipatterns() {
    }

    // DEMO_ONLY — SQL injection via concatenation (critical)
    public static ResultSet demoUnsafeFindByVin(String vin) throws Exception {
        Connection conn = DriverManager.getConnection(DEMO_JDBC_URL, DEMO_DB_USER, DEMO_DB_PASSWORD);
        Statement stmt = conn.createStatement();
        String sql = "SELECT * FROM vehicles WHERE vin = '" + vin + "'";
        return stmt.executeQuery(sql);
    }

    // DEMO_ONLY — Command injection (critical)
    public static void demoPingHost(String host) throws Exception {
        Runtime.getRuntime().exec("ping -c 1 " + host);
    }

    // DEMO_ONLY — Weak PRNG for security-sensitive value (critical)
    public static String demoWeakSessionToken() {
        return Long.toHexString(new Random().nextLong());
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

    // DEMO_ONLY — Path traversal pattern (critical / CWE-22)
    public static byte[] demoReadAttachment(String userSuppliedName) throws Exception {
        Path base = Paths.get("/var/autocare/uploads");
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
