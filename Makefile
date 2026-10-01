# Repository-local developer entry points. Keep policy in Maven and repository scripts.
.DEFAULT_GOAL := help

MVN ?= mvn
PYTHON ?= python3
SH ?= sh
MVN_FLAGS ?=
DIST_VALIDATE_FLAGS ?= --require-clean-source
JAVA_TEST_JOBS ?= 6
PROTOS_TEST_JOBS ?= 8

# DAP and the real GraalVM LSP tests own Graal tooling state and are not safe
# in the class-parallel lane.
JAVA_SERIAL_TESTS := ProtosI026FDapBehaviorTest,ProtosI026GLspCapabilityTest,ProtosI026GLspTransportTest,ProtosTestToolPerf017AdmissionTest
JAVA_SERIAL_TEST_EXCLUDES := **/ProtosI026FDapBehaviorTest.java,**/ProtosI026GLspCapabilityTest.java,**/ProtosI026GLspTransportTest.java,**/ProtosTestToolPerf017AdmissionTest.java
JAVA_PARALLEL_EXCLUDES := $(JAVA_SERIAL_TEST_EXCLUDES)
JAVA_STRESS_TESTS := ProtosJsonParserStress
# TEST008: retained current-run log and slow-test guard for `make test-java`.
JAVA_TEST_LOG := target/test-java.log
JAVA_SUREFIRE_REPORTS := target/surefire-reports
JAVA_SLOW_TEST_GUARD := $(PYTHON) tools/java_slow_test_guard.py
JAVA_SLOW_TEST_ALLOWLIST := tools/java_slow_tests_allowlist.txt

.PHONY: help toolchain compile build test test-java test-java-parallel test-java-serial test-java-stress test-protos check verify clean dist dist-validate

help:
	@printf '%s\n' \
		'Protos developer targets:' \
		'  make toolchain      Verify the selected development toolchain contract' \
		'  make compile        Compile production sources only (incremental)' \
		'  make build          Clean and package without executing tests' \
		'  make test           Run Java tests, then Protos tests' \
		'  make test-java      Run the ordinary Java/JUnit test suite' \
		'  make test-java-stress  Run explicit Java stress validation' \
		'  make test-protos    Build Protos and run the native Protos test suite' \
		'  make check          Verify the toolchain, then run both test suites' \
		'  make verify         Run a clean Maven verify lifecycle' \
		'  make clean          Remove Maven build output' \
		'  make dist           Build the portable distribution' \
		'  make dist-validate  Build and validate the portable distribution' \
		'' \
		'Overrides: MVN=... PYTHON=... SH=... MVN_FLAGS=... JAVA_TEST_JOBS=... PROTOS_TEST_JOBS=... DIST_VALIDATE_FLAGS=...'

toolchain:
	$(PYTHON) tools/verify_toolchain.py --mode check --scope development

compile:
	$(MVN) $(MVN_FLAGS) compile

build:
	$(MVN) $(MVN_FLAGS) clean package -DskipTests

test: test-java test-protos

test-java:
	$(JAVA_SLOW_TEST_GUARD) reset --reports $(JAVA_SUREFIRE_REPORTS) --log $(JAVA_TEST_LOG)
	$(JAVA_SLOW_TEST_GUARD) run --log $(JAVA_TEST_LOG) -- $(MAKE) --no-print-directory test-java-parallel
	$(JAVA_SLOW_TEST_GUARD) run --log $(JAVA_TEST_LOG) -- $(MAKE) --no-print-directory test-java-serial
	$(JAVA_SLOW_TEST_GUARD) check --reports $(JAVA_SUREFIRE_REPORTS) --allowlist $(JAVA_SLOW_TEST_ALLOWLIST)

test-java-parallel:
	$(MVN) $(MVN_FLAGS) \
		-Djunit.jupiter.execution.parallel.enabled=true \
		-Djunit.jupiter.execution.parallel.mode.default=same_thread \
		-Djunit.jupiter.execution.parallel.mode.classes.default=concurrent \
		-Djunit.jupiter.execution.parallel.config.strategy=fixed \
		-Djunit.jupiter.execution.parallel.config.fixed.parallelism=$(JAVA_TEST_JOBS) \
		"-Dsurefire.excludes=$(JAVA_PARALLEL_EXCLUDES)" \
		test

test-java-serial:
	$(MVN) $(MVN_FLAGS) -Dtest=$(JAVA_SERIAL_TESTS) test

test-java-stress:
	$(MVN) $(MVN_FLAGS) \
		-Djunit.jupiter.execution.parallel.enabled=false \
		-Dtest=$(JAVA_STRESS_TESTS) \
		test

test-protos:
	$(MVN) $(MVN_FLAGS) package -DskipTests
	@start=$$(date +%s); \
	bin/protos test --jobs $(PROTOS_TEST_JOBS); \
	status=$$?; \
	end=$$(date +%s); \
	printf 'Protos tests total time: %s s\n' "$$((end - start))"; \
	exit $$status

check: toolchain test

verify:
	$(MVN) $(MVN_FLAGS) clean verify

clean:
	$(MVN) $(MVN_FLAGS) clean

dist:
	$(PYTHON) dist/build_portable.py

dist-validate: dist
	$(SH) dist/validate_portable.sh $(DIST_VALIDATE_FLAGS)
