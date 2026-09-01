FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825

USER root

RUN microdnf install -y \
        findutils \
        git \
        gzip \
        shadow-utils \
        tar \
        unzip \
        xz \
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

ARG CLAUDE_CODE_VERSION=2.1.252

RUN mkdir -p /home/vscode/.local/npm \
    && npm install --global \
        --allow-scripts=@anthropic-ai/claude-code \
        "@anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}" \
    && claude --version