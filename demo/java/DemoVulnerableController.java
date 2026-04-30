// DEMO_ONLY — Intentionally vulnerable code used to showcase the
// Code Intelligence Platform scanner. DO NOT deploy. Safe to delete this file.
package com.autocare.demo;

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

public class DemoVulnerableController {

    // DEMO_ONLY — Hardcoded credentials (Semgrep/SpotBugs/gitleaks)
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "SuperSecret123!";
    private static final String JDBC_URL = "jdbc:mysql://prod-db.internal:3306/autocare";

    // DEMO_ONLY — Hardcoded API key (gitleaks/secret scanner)
    private static final String STRIPE_API_KEY = "sk_demo_FAKE_DO_NOT_USE_REPLACE_BEFORE_PROD_0000000000";

    // DEMO_ONLY — SQL injection via string concatenation
    public ResultSet findUserByName(String username) throws Exception {
        Connection conn = DriverManager.getConnection(JDBC_URL, DB_USER, DB_PASSWORD);
        Statement stmt = conn.createStatement();
        String query = "SELECT * FROM users WHERE username = '" + username + "'";
        return stmt.executeQuery(query);
    }

    // DEMO_ONLY — Command injection via Runtime.exec on tainted input
    public String pingHost(String host) throws Exception {
        Process p = Runtime.getRuntime().exec("ping -c 1 " + host);
        p.waitFor();
        return "ok";
    }

    // DEMO_ONLY — Insecure random for security-sensitive token
    public String generateSessionToken() {
        Random rnd = new Random();
        return Long.toHexString(rnd.nextLong());
    }

    // DEMO_ONLY — Weak crypto (MD5)
    public String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(password.getBytes());
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // DEMO_ONLY — XXE-prone XML parsing pattern (illustrative)
    public void parseXml(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory dbf =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
        db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // DEMO_ONLY — Unsafe Java deserialization on attacker-controlled bytes (critical / CWE-502)
    public Object deserializeUntrustedPayload(byte[] serialized) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return ois.readObject();
        }
    }

    // DEMO_ONLY — Path traversal: user-controlled filename joined to base path (critical / CWE-22)
    public byte[] readCustomerAttachment(String filename) throws Exception {
        Path base = Paths.get("/var/autocare/attachments");
        Path resolved = base.resolve(filename).normalize();
        return Files.readAllBytes(resolved);
    }

    // DEMO_ONLY — SSRF: server-side fetch to caller-supplied URL (critical / CWE-918)
    public String fetchExternalDiagnostics(String urlString) throws Exception {
        URL url = new URL(urlString);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
