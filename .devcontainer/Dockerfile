FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825

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
        python39 \
    && microdnf clean all

ARG MAVEN_VERSION=3.9.9

RUN curl --fail --location --silent --show-error \
        "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        --output /tmp/apache-maven.tar.gz \
    && tar --extract --gzip \
        --file /tmp/apache-maven.tar.gz \
        --directory /opt \
    && ln --symbolic "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven \
    && ln --symbolic /opt/maven/bin/mvn /usr/local/bin/mvn \
    && rm /tmp/apache-maven.tar.gz

ENV MAVEN_HOME=/opt/maven
ENV PATH="/opt/maven/bin:${PATH}"

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

ARG NODE_VERSION=22.14.0

RUN curl --fail --location --silent --show-error \
        "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
        --output /tmp/node.tar.xz \
    && mkdir -p /opt/node \
    && tar --extract --xz \
        --file /tmp/node.tar.xz \
        --directory /opt/node \
        --strip-components=1 \
    && ln --symbolic /opt/node/bin/node /usr/local/bin/node \
    && ln --symbolic /opt/node/bin/npm /usr/local/bin/npm \
    && ln --symbolic /opt/node/bin/npx /usr/local/bin/npx \
    && rm /tmp/node.tar.xz

RUN useradd --create-home --shell /bin/bash vscode

USER vscode

ENV NPM_CONFIG_PREFIX=/home/vscode/.local/npm
ENV PATH="/home/vscode/.local/npm/bin:/opt/node/bin:/opt/maven/bin:${PATH}"
