FROM ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol8-20260825

RUN microdnf install -y \
        findutils \
        git \
        gzip \
        shadow-utils \
        tar \
        unzip \
    && microdnf clean all


ARG MAVEN_VERSION=3.9.9
RUN curl --fail --location --silent --show-error \
        "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        --output /tmp/apache-maven.tar.gz \
    && tar --extract --gzip --file /tmp/apache-maven.tar.gz --directory /opt \
    && ln --symbolic "/opt/apache-maven-${MAVEN_VERSION}" /opt/maven \
    && ln --symbolic /opt/maven/bin/mvn /usr/local/bin/mvn \
    && rm /tmp/apache-maven.tar.gz \
    && useradd --create-home --shell /bin/bash vscode
ENV MAVEN_HOME=/opt/maven

USER vscode
