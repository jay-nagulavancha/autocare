# Demo Issues (DEMO_ONLY)

This `demo/` directory, **`vehicle-maintenance-service`** DEMO stubs, and a few marked
snippets in `pom.xml` contain **intentionally vulnerable code, infrastructure, and configuration**
used to showcase the
[Code Intelligence Platform](../../code-intelligence-platform) scanner and its
remediation/PR flow. These are **safe to delete at any time**.

Every implant is tagged with `DEMO_ONLY` so you can find and revert them quickly:

```bash
git grep -n DEMO_ONLY
```

## Files added

| Path | Analyzer(s) | Issues |
| --- | --- | --- |
| `demo/java/DemoVulnerableController.java` | `security` (semgrep), `secrets` (gitleaks) | Hardcoded creds, hardcoded API key, SQL injection, command injection, weak crypto (MD5), insecure `Random` for tokens, XXE-prone XML parsing, **unsafe deserialization (`ObjectInputStream`)**, **path traversal (`Paths.resolve` + user filename)**, **SSRF (`URL.openStream` + user URL)** |
| `demo/secrets/.env.demo` | `secrets` (gitleaks) | Fake AWS access key + secret key, GitHub token, Slack bot token, Stripe key, DB password, JWT secret |
| `demo/terraform/insecure.tf` | `infra` (checkov) | SG open `0.0.0.0/0` on 22/3306, public S3 bucket, unencrypted publicly-accessible RDS, IAM policy `*:*` |
| `demo/docker/Dockerfile` | `container` (trivy/dockle) | `ubuntu:latest`, runs as root, hardcoded creds, `curl \| sh`, no HEALTHCHECK, no `--no-install-recommends` |
| `demo/k8s/insecure-deployment.yaml` | `infra` (checkov) | `privileged`, `hostPID`, `hostNetwork`, `nginx:latest`, root UID, secrets in plain `env`, writable root FS |

## Files modified

| Path | Analyzer(s) | Issues |
| --- | --- | --- |
| `autocare/user-auth-service/pom.xml` | `oss` (dependency-check) | Adds `log4j-core 2.14.1` (CVE-2021-44228, Log4Shell), `commons-text 1.9` (CVE-2022-42889, Text4Shell), `snakeyaml 1.29` (CVE-2022-1471) |
| `autocare/vehicle-maintenance-service/pom.xml` | `oss` (dependency-check) | **DEMO_ONLY:** direct `commons-text` **1.9** (CVE-2022-42889) — not imported by app code |

## Files added (vehicle-maintenance-service)

| Path | Analyzer(s) | Issues |
| --- | --- | --- |
| `autocare/vehicle-maintenance-service/src/main/java/com/autocare/maintenance/demo/DemoScannerAntipatterns.java` | `security` (Semgrep / SpotBugs) | Hardcoded DB creds + fake token, SQLi, command injection, MD5, insecure `Random`, deserialization, path traversal, SSRF, XXE-prone XML (**not** a `@Component`; never called from production code) |

## How to revert

```bash
# Remove the demo folder
rm -rf demo/

# Revert pom.xml changes (user-auth + maintenance)
git checkout -- autocare/user-auth-service/pom.xml autocare/vehicle-maintenance-service/pom.xml

# Remove maintenance DEMO_ONLY Java stub
rm -f autocare/vehicle-maintenance-service/src/main/java/com/autocare/maintenance/demo/DemoScannerAntipatterns.java
```

Or in one shot:

```bash
git checkout -- autocare/user-auth-service/pom.xml autocare/vehicle-maintenance-service/pom.xml && rm -rf demo/ && rm -f autocare/vehicle-maintenance-service/src/main/java/com/autocare/maintenance/demo/DemoScannerAntipatterns.java
```

(`rm -rf demo/` removes all demo implants including `demo/k8s/`.)

## Suggested demo flow

1. Run a full scan against `autocare` with all analyzers enabled
   (`security`, `oss`, `secrets`, `infra`, `container`).
2. Show the categorized findings (severity counts, tool names per finding).
3. Trigger the PR remediation flow to auto-generate fix PRs:
   - Bump `log4j-core` to `2.17.2+`, `commons-text` to `1.10.0+` (auth + maintenance POMs),
     `snakeyaml` to `2.0+`.
   - Fix or delete `DemoScannerAntipatterns.java` (maintenance) using the same patterns as `demo/java/DemoVulnerableController.java`.
   - Replace MD5 with SHA-256/Argon2.
   - Replace `Random` with `SecureRandom`.
   - Parameterize the SQL statement with `PreparedStatement`.
   - Restrict the open SG ingress, encrypt RDS, lock down S3.
   - Harden `demo/k8s/insecure-deployment.yaml`: drop `privileged` / `hostPID` / `hostNetwork`, pin image digest or tag, use secrets refs, non-root.
   - Pin Dockerfile to a specific tag, drop to non-root, add HEALTHCHECK.
