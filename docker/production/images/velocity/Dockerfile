FROM eclipse-temurin:25-jdk

ARG VELOCITY_VERSION=latest
ARG VELOCITY_BUILD_CHANNEL=STABLE
ARG USER_AGENT="hycopy-docker/2.0 (https://example.net)"

ENV VELOCITY_VERSION="${VELOCITY_VERSION}"
ENV VELOCITY_BUILD_CHANNEL="${VELOCITY_BUILD_CHANNEL}"
ENV USER_AGENT="${USER_AGENT}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl jq ca-certificates \
    && mkdir -p /server/plugins/hycopyproxy /bootstrap/proxy \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /server

COPY install-velocity.sh /usr/local/bin/install-velocity.sh
RUN chmod +x /usr/local/bin/install-velocity.sh

COPY PatchVelocityLmbda.java /usr/local/bin/PatchVelocityLmbda.java

COPY entrypoint.sh /usr/local/bin/velocity-entrypoint.sh
RUN chmod +x /usr/local/bin/velocity-entrypoint.sh

ENTRYPOINT ["/usr/local/bin/velocity-entrypoint.sh"]
