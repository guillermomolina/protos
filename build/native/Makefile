# Native Image developer entry points.
# Keep Native-specific orchestration isolated from the repository root Makefile.

.DEFAULT_GOAL := help

ROOT := $(abspath $(CURDIR)/../..)

MVN ?= mvn
MVN_FLAGS ?=
BASH ?= bash

NATIVE_BIN := $(ROOT)/target/native/protos
NATIVE_INIT_ARGS := $(ROOT)/target/native-image-init.args
NATIVE_TEST := $(ROOT)/build/native/test-native.sh

# I130-C2: the Native JUnit TLS executable is separate from $(NATIVE_BIN).
NATIVE_TLS_TESTS_BIN := $(ROOT)/target/native-tests
NATIVE_TLS_TEST_REPORTS := $(ROOT)/target/native-test-reports
NATIVE_TLS_TEST_IDS := $(ROOT)/target/maven-surefire-plugin-test-ids
NATIVE_TLS_TEST_PACKAGE := com.guillermomolina.protos.execution
NATIVE_TLS_TEST_CLASSES := ProtosTlsClientIntegrationTest ProtosTlsTrustTest

.PHONY: help build test clean

help:
	@printf '%s\n' \
		'Protos Native Image targets:' \
		'  make -C build/native build   Build the Stage1 Native Image executable' \
		'  make -C build/native test    Build and validate interpreter-only Native (PLAT045)' \
		'  make -C build/native clean   Remove Native Image-specific build artifacts' \
		'' \
		'Overrides: MVN=... MVN_FLAGS=... BASH=...'

build:
	$(MVN) -f $(ROOT)/pom.xml $(MVN_FLAGS) -Pnative -DskipTests package

test: build
	$(BASH) $(NATIVE_TEST)
	rm -rf $(NATIVE_TLS_TESTS_BIN) $(NATIVE_TLS_TEST_REPORTS) $(NATIVE_TLS_TEST_IDS)
	$(MVN) -f $(ROOT)/pom.xml $(MVN_FLAGS) -Pnative-tls-tests \
		-Dtest=ProtosTlsClientIntegrationTest,ProtosTlsTrustTest test
	@test -x $(NATIVE_TLS_TESTS_BIN) || \
		{ echo 'NATIVE_TLS_JUNIT_EXECUTABLE=MISSING' >&2; exit 1; }
	@for class in $(NATIVE_TLS_TEST_CLASSES); do \
		grep -rqF --include='*.xml' "classname=\"$(NATIVE_TLS_TEST_PACKAGE).$$class\"" \
			$(NATIVE_TLS_TEST_REPORTS) || \
			{ echo "NATIVE_TLS_JUNIT_CLASS_NOT_RUN=$$class" >&2; exit 1; }; \
	done
	@if grep -rqE --include='*.xml' '<(failure|error)[ >]' $(NATIVE_TLS_TEST_REPORTS); then \
		echo 'NATIVE_TLS_JUNIT=FAIL' >&2; exit 1; \
	fi
	@echo 'NATIVE_TLS_PEER_INTEGRATION=PASS'
	@echo 'NATIVE_TLS_TRUST_PROVENANCE=PASS'

clean:
	rm -rf $(ROOT)/target/native
	rm -f $(NATIVE_INIT_ARGS)
	rm -rf $(NATIVE_TLS_TESTS_BIN) $(NATIVE_TLS_TEST_REPORTS) $(NATIVE_TLS_TEST_IDS)
