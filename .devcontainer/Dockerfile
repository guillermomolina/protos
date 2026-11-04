FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol10@sha256:2800b5bd11cbb9bf11c92591d05ea37d9a735522bdc0e86827302d998843ad79

USER root

RUN microdnf install -y \
        file \
        binutils \
        findutils \
        diffutils \
        git \
        gzip \
        shadow-utils \
        tar \
        unzip \
        xz \
        procps \
        time \
        sudo \
    && microdnf clean all

RUN microdnf install -y \
        --enablerepo=ol10_codeready_builder \
        maven \
        maven-unbound \
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
