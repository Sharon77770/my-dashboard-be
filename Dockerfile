FROM maven:3.9.11-eclipse-temurin-21 AS build
RUN apt-get update && apt-get install -y --no-install-recommends python3 git tmux \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN python3 -m unittest discover -s src/test/python -v
RUN mvn -B verify

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends util-linux bash docker.io openssh-client git python3 tmux ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 dashboard \
    && useradd --uid 10001 --gid dashboard --home-dir /app/data/home --no-create-home dashboard \
    && mkdir -p /app/data/files \
    && chown -R dashboard:dashboard /app
WORKDIR /app
COPY --from=build --chown=dashboard:dashboard /workspace/target/my-dashboard-be-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=dashboard:dashboard docker/start.sh /app/start.sh
ENV HOME=/app/data/home
USER dashboard
EXPOSE 8080
ENTRYPOINT ["sh", "/app/start.sh"]
