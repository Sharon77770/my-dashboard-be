FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends samba \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /run/samba /var/lib/samba/private

COPY smb.conf /etc/samba/smb.conf
COPY entrypoint.sh /usr/local/bin/nas-samba-entrypoint
RUN chmod 0755 /usr/local/bin/nas-samba-entrypoint

EXPOSE 445/tcp
ENTRYPOINT ["/usr/local/bin/nas-samba-entrypoint"]
