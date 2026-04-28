# DEMO_ONLY — Intentionally insecure Dockerfile used to demo Trivy/Dockle.
# DO NOT use in production. Safe to delete this file.

FROM ubuntu:latest

# DEMO_ONLY — Running as root
USER root

# DEMO_ONLY — apt-get install without --no-install-recommends and without pinned versions
RUN apt-get update && \
    apt-get install -y curl wget netcat-openbsd openssh-server sudo && \
    rm -rf /var/lib/apt/lists/*

# DEMO_ONLY — Hardcoded credentials in image
ENV DB_PASSWORD=SuperSecret123!
ENV AWS_SECRET_ACCESS_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY

# DEMO_ONLY — Pulling and executing remote script with curl | sh
RUN curl -sSL http://example.com/install.sh | sh

# DEMO_ONLY — Exposing privileged port
EXPOSE 22

# DEMO_ONLY — No HEALTHCHECK, no USER switch, no read-only fs

CMD ["/bin/bash"]
