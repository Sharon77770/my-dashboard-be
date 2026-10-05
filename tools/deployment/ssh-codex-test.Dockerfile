FROM maven:3.9.11-eclipse-temurin-21
RUN apt-get update && apt-get install -y --no-install-recommends openssh-server docker.io python3 git tmux \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -m -s /bin/bash codexcheck && mkdir -p /run/sshd
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY tools/deployment/docker-log-test-proxy.py /opt/docker-log-test-proxy.py
