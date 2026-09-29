FROM ghcr.io/graalvm/native-image-community:25i4-25.0.4.1.1-ol10

USER root

RUN microdnf install -y \
        --enablerepo=ol10_codeready_builder \
        maven \
        maven-unbound \
    && microdnf clean all

WORKDIR /workspace

ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV MAVEN_OPTS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

ENTRYPOINT []
