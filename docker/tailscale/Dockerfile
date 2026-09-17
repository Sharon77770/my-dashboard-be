FROM tailscale/tailscale@sha256:8c42c4574ab066384fcb72f69e086a2ff1dd3652eb6f56856cee34bcf0d2f680
RUN apk add --no-cache python3
COPY bridge.py test_bridge.py start.sh /opt/dashboard-tailscale/
RUN cd /opt/dashboard-tailscale && python3 -m unittest -v test_bridge
