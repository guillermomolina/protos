FROM ghcr.io/graalvm/native-image-community:25i3-25.0.4.1-ol10-20260825

USER root

RUN microdnf install -y \
        maven \
    && microdnf clean all

WORKDIR /workspace

ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV MAVEN_OPTS="--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

ENTRYPOINT []
