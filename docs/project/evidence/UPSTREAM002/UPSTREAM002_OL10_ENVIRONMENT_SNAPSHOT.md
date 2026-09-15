# UPSTREAM002 — OL10 container environment snapshot

Captured 2026-09-15 in the devcontainer built from
`ghcr.io/graalvm/graalvm-community:25i3-25.0.4.1-ol10-20260825`
(container `distracted_fermat`, image `vsc-protos-6c8bfb7372c8ce6f63705399735bd1c55d8a5021dff1acc1b06d9226640d72e3-uid`),
sharing the repository checkout through the devcontainer workspace mount.

## OS identity

```text
$ grep -E "^(NAME|VERSION)=" /etc/os-release
NAME="Oracle Linux Server"
VERSION="10.2"
```

## Java identity

```text
$ echo $JAVA_HOME
/opt/graalvm-community-java25i3

$ /opt/graalvm-community-java25i3/bin/java -version
openjdk version "25.0.4.1" 2026-08-18
OpenJDK Runtime Environment GraalVM CE 25.3.4.1+1.1 (build 25.0.4.1+1-jvmci-25.3-b22)
OpenJDK 64-Bit Server VM GraalVM CE 25.3.4.1+1.1 (build 25.0.4.1+1-jvmci-25.3-b22, mixed mode, sharing)
```

The bare PATH `java` is the OL10 system JDK (installed as a dependency of the OS
`maven` package); Maven builds run under `JAVA_HOME` (GraalVM):

```text
$ java -version
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment (Red_Hat-21.0.12.1.1-1.1) (build 21.0.12.1+1-LTS)
OpenJDK 64-Bit Server VM (Red_Hat-21.0.12.1.1-1.1) (build 21.0.12.1+1-LTS, mixed mode, sharing)
```

## Maven identity

```text
$ mvn -version
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by org.fusesource.jansi.internal.JansiLoader in an unnamed module (file:/usr/share/maven/lib/jansi-2.4.1.jar)
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled

Apache Maven 3.9.9 (Red Hat 3.9.9-3)
Maven home: /usr/share/maven
Java version: 25.0.4.1, vendor: GraalVM Community, runtime: /opt/graalvm-community-java25i3
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "7.2.4-arch1-2", arch: "amd64", family: "unix"
```

The JVM native-access warnings are emitted by Maven 3.9.9's bundled Jansi 2.4.1
under JDK 25; they are startup warnings and did not affect the build.

## Python identity

```text
$ which python3 && python3 --version
/usr/bin/python3
Python 3.12.13
```

## GitHub CLI identity

```text
$ gh --version
gh version 2.100.0 (2026-09-03)
```

The pinned/checksum-verified GitHub CLI provisioning retained from the OL8
Dockerfile works unchanged on OL10.

## Interpretation limits

This snapshot records one devcontainer instance on the host kernel
`7.2.4-arch1-2`. It is evidence for the pinned OL10 image content, not a
portability claim beyond that image.
