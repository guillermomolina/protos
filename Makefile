# Repository-local developer entry points. Keep policy in Maven and repository scripts.
.DEFAULT_GOAL := help

MVN ?= mvn
PYTHON ?= python3
SH ?= sh
MVN_FLAGS ?=
DIST_VALIDATE_FLAGS ?= --require-clean-source
MAVEN_GUARD ?= $(PYTHON) scripts/maven_worktree_guard.py --repo . --

.PHONY: help toolchain compile build test check verify clean dist dist-validate

help:
	@printf '%s\n' \
		'Protos developer targets:' \
		'  make toolchain      Verify the selected development toolchain contract' \
		'  make compile        Compile production sources only (incremental)' \
		'  make build          Clean and package without executing tests' \
		'  make test           Clean and run the complete Maven test suite' \
		'  make check          Verify the toolchain, then run the complete test suite' \
		'  make verify         Run a clean Maven verify lifecycle' \
		'  make clean          Remove Maven build output' \
		'  make dist           Build the portable distribution' \
		'  make dist-validate  Build and validate the portable distribution' \
		'' \
		'Overrides: MVN=... PYTHON=... SH=... MVN_FLAGS=... DIST_VALIDATE_FLAGS=...'

toolchain:
	$(PYTHON) tools/verify_toolchain.py --mode check --scope development

compile:
	$(MAVEN_GUARD) $(MVN) $(MVN_FLAGS) compile

build:
	$(MAVEN_GUARD) $(MVN) $(MVN_FLAGS) clean package -DskipTests

test:
	$(MAVEN_GUARD) $(MVN) $(MVN_FLAGS) clean test

check: toolchain test

verify:
	$(MAVEN_GUARD) $(MVN) $(MVN_FLAGS) clean verify

clean:
	$(MAVEN_GUARD) $(MVN) $(MVN_FLAGS) clean

dist:
	$(PYTHON) dist/build_portable.py

dist-validate: dist
	$(SH) dist/validate_portable.sh $(DIST_VALIDATE_FLAGS)
