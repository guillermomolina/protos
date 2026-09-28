FROM ghcr.io/graalvm/graalvm-community:25i4-25.0.4.1.1-ol10@sha256:a7b4810d7c755e9627feaa1459eb5a93338643b16d745d4f3fc86db71e5da7f5

USER root

RUN microdnf install -y \
        findutils \
        diffutils \
        git \
        gzip \
        shadow-utils \
        tar \
        unzip \
        xz \
        maven \
        procps \
        time \
        sudo \
    && microdnf clean all

ENV PATH="${JAVA_HOME}/bin:${PATH}"

ARG GH_VERSION=2.100.0
ARG GH_LINUX_AMD64_SHA256=e4d4bb4498e8d007abe545b6568926793ace1b6447da598294a610018cb164be

RUN curl --fail --location --silent --show-error \
        "https://github.com/cli/cli/releases/download/v${GH_VERSION}/gh_${GH_VERSION}_linux_amd64.tar.gz" \
        --output /tmp/gh.tar.gz \
    && echo "${GH_LINUX_AMD64_SHA256}  /tmp/gh.tar.gz" | sha256sum --check --strict - \
    && tar --extract --gzip \
        --file /tmp/gh.tar.gz \
        --directory /opt \
    && ln --symbolic "/opt/gh_${GH_VERSION}_linux_amd64/bin/gh" /usr/local/bin/gh \
    && gh --version \
    && rm /tmp/gh.tar.gz

RUN useradd --create-home --shell /bin/bash vscode \
    && echo "vscode ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/vscode \
    && chmod 0440 /etc/sudoers.d/vscode

USER vscode

RUN curl -fsSL https://claude.ai/install.sh | bash

WORKDIR /workspaces/protos

ENV MAVEN_OPTS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"
