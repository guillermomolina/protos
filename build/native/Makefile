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

.PHONY: help build test clean

help:
	@printf '%s\n' \
		'Protos Native Image targets:' \
		'  make -C build/native build   Build the Stage1 Native Image executable' \
		'  make -C build/native test    Build and validate Native guest runtime compilation' \
		'  make -C build/native clean   Remove Native Image-specific build artifacts' \
		'' \
		'Overrides: MVN=... MVN_FLAGS=... BASH=...'

build:
	$(MVN) -f $(ROOT)/pom.xml $(MVN_FLAGS) -Pnative -DskipTests package

test: build
	$(BASH) $(NATIVE_TEST)

clean:
	rm -rf $(ROOT)/target/native
	rm -f $(NATIVE_INIT_ARGS)
