FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825

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
    && microdnf clean all

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

RUN useradd --create-home --shell /bin/bash vscode

USER vscode
