FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends openssh-server sudo python3 ca-certificates && rm -rf /var/lib/apt/lists/* \
    && useradd -m -s /bin/bash tester && usermod -aG sudo tester && mkdir -p /run/sshd
COPY start.sh /usr/local/bin/fixture-start
RUN chmod 755 /usr/local/bin/fixture-start
ENTRYPOINT ["/usr/local/bin/fixture-start"]
