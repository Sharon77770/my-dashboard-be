FROM maven:3.9.11-eclipse-temurin-21 AS build
RUN apt-get update && apt-get install -y --no-install-recommends python3 git tmux \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN python3 -m unittest discover -s src/test/python -v
RUN mvn -B verify

FROM docker:28.5.2-cli AS docker-tools

FROM eclipse-temurin:21-jdk-jammy AS workspace-base
# Trusted deployment configuration: extra apt package names, installed at image build time.
ARG WORKSPACE_APT_PACKAGES=""
ARG WORKSPACE_DEVELOPMENT="false"
RUN apt-get update && apt-get install -y --no-install-recommends \
    util-linux bash coreutils findutils grep sed gawk tar \
    openssh-client git tmux ca-certificates bubblewrap \
    build-essential pkg-config python3 python3-pip python3-venv \
    curl wget jq ripgrep procps iproute2 iputils-ping dnsutils netcat-openbsd \
    less vim-tiny zip unzip ${WORKSPACE_APT_PACKAGES} \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 dashboard \
    && useradd --uid 10001 --gid dashboard --home-dir /app/data/home --no-create-home dashboard \
    && mkdir -p /app/data/files /app/data/home \
    && chown -R dashboard:dashboard /app
# Development is opt-in; production retains its non-root, no-sudo contract.
RUN if [ "$WORKSPACE_DEVELOPMENT" = "true" ]; then \
      apt-get update && apt-get install -y --no-install-recommends sudo \
      && printf 'dashboard ALL=(ALL) NOPASSWD: ALL\n' > /etc/sudoers.d/dashboard-development \
      && chmod 0440 /etc/sudoers.d/dashboard-development \
      && visudo -cf /etc/sudoers.d/dashboard-development \
      && rm -rf /var/lib/apt/lists/*; \
    elif [ "$WORKSPACE_DEVELOPMENT" != "false" ]; then exit 1; fi
COPY --from=docker-tools /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-tools /usr/local/libexec/docker/cli-plugins /usr/local/libexec/docker/cli-plugins
WORKDIR /app
ENV HOME=/app/data/home
USER dashboard

FROM workspace-base AS runtime
COPY --from=build --chown=dashboard:dashboard /workspace/target/my-dashboard-be-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=dashboard:dashboard docker/start.sh /app/start.sh
EXPOSE 8080
ENTRYPOINT ["sh", "/app/start.sh"]
