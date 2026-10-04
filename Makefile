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
JAVA_SLOW_TEST_BASELINE := tools/java_slow_test_baseline.txt
JAVA_SLOW_TEST_STATE := target/java-slow-test
JAVA_CONFIRM_TESTS ?=
# Project-owner decision (TEST008 follow-up): slow-test regression verdicts
# warn instead of failing local validation. Guard configuration/environment
# errors remain fail-closed locally; under CI (CI=true) every guard verdict
# remains advisory.
JAVA_SLOW_TEST_MODE ?= $(if $(filter true,$(CI)),--advisory,--warn-regressions)
JAVA_CONFIRM_REPORTS ?= $(JAVA_SLOW_TEST_STATE)/confirmation-reports
# PERF030-F: static LocalRangeAccessor PE-index guard (check-local-range-index-pe).
LOCAL_RANGE_PE_GUARD_BASELINE := tools/java_local_range_pe_guard_baseline.json
# PERF030-G: exact expected PE reachability of every determinate sink.
LOCAL_RANGE_PE_REACHABILITY_BASELINE := tools/java_local_range_pe_reachability_baseline.json
LOCAL_RANGE_PE_GUARD_REPORT := target/local-range-pe-guard-report.json
# TEST009-A: static LocalRangeAccessor receiver/BytecodeNode PE guard.
LOCAL_RANGE_OPERAND_PE_BASELINE := tools/java_local_range_operand_pe_baseline.json
LOCAL_RANGE_OPERAND_PE_REPORT := target/local-range-operand-pe-guard-report.json
# TEST009-B: static LocalAccessor/MaterializedLocalAccessor PE guard.
LOCAL_ACCESSOR_PE_BASELINE := tools/java_local_accessor_pe_baseline.json
LOCAL_ACCESSOR_PE_REPORT := target/local-accessor-pe-guard-report.json
# TEST009-C: static Bytecode API (BytecodeNode/BytecodeRootNodes/BytecodeLocation) PE-argument guard.
BYTECODE_API_PE_BASELINE := tools/java_bytecode_api_pe_baseline.json
BYTECODE_API_PE_REPORT := target/bytecode-api-pe-guard-report.json

.PHONY: help toolchain compile build test test-java java-test-phase test-java-parallel test-java-serial test-java-confirm test-java-stress test-local-range-pe-guard check-local-range-index-pe check-local-range-operands-pe check-local-accessor-pe check-bytecode-api-pe test-protos check verify clean artifacts artifacts-verify artifacts-publish-d064 dist dist-validate

help:
	@printf '%s\n' \
		'Protos developer targets:' \
		'  make toolchain      Verify the selected development toolchain contract' \
		'  make compile        Compile production sources only (incremental)' \
		'  make build          Clean and package without executing tests' \
		'  make test           Run Java tests, then Protos tests' \
		'  make test-java      Run the ordinary Java/JUnit test suite' \
		'  make test-java-stress  Run explicit Java stress validation' \
		'  make check-local-range-index-pe  Static LocalRangeAccessor PE-index guard:' \
		'                      self-tests, baseline and PE-reachability checks' \
		'  make check-local-range-operands-pe  Static LocalRangeAccessor receiver and' \
		'                      BytecodeNode PE guard: self-tests and topology check' \
		'  make check-local-accessor-pe  Static LocalAccessor/MaterializedLocalAccessor' \
		'                      receiver/BytecodeNode/declaring-node PE guard' \
		'  make check-bytecode-api-pe  Static BytecodeNode/BytecodeRootNodes/BytecodeLocation' \
		'                      PE-argument guard: self-tests and topology check' \
		'  make test-local-range-pe-guard  Compatibility alias of check-local-range-index-pe' \
		'  make test-protos    Build Protos and run the native Protos test suite' \
		'  make check          Verify the toolchain, run the static LocalRange,' \
		'                      LocalAccessor and Bytecode API PE guards, then run both test suites' \
		'  make verify         Run a clean Maven verify lifecycle' \
		'  make clean          Remove Maven build output' \
		'  make artifacts      Build the canonical exact-revision artifact set' \
		'                      (Native + portable JVM + D064 docs + manifest; not a release)' \
		'  make artifacts-verify  Re-verify target/artifact-set against its manifest' \
		'  make artifacts-publish-d064  Publish its D064 to GHCR as rev-<sha> (explicit;' \
		'                      needs GHCR_USERNAME/GHCR_TOKEN; never builds or releases)' \
		'  make dist           Build only the portable JVM distribution' \
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

# TEST008/PLAT047: machine controls bracket the timed Java phases; the check
# confirms suspects once (test-java-confirm) and decides PASS/FAIL/ERROR.
test-java:
	$(JAVA_SLOW_TEST_GUARD) reset --reports $(JAVA_SUREFIRE_REPORTS) --log $(JAVA_TEST_LOG) --state $(JAVA_SLOW_TEST_STATE)
	$(JAVA_SLOW_TEST_GUARD) controls --state $(JAVA_SLOW_TEST_STATE) --jobs $(JAVA_TEST_JOBS)
	@$(MAKE) --no-print-directory java-test-phase JAVA_TEST_PHASE=test-java-parallel
	@$(MAKE) --no-print-directory java-test-phase JAVA_TEST_PHASE=test-java-serial
	$(JAVA_SLOW_TEST_GUARD) check --reports $(JAVA_SUREFIRE_REPORTS) --baseline $(JAVA_SLOW_TEST_BASELINE) \
		--state $(JAVA_SLOW_TEST_STATE) --jobs $(JAVA_TEST_JOBS) --log $(JAVA_TEST_LOG) \
		--confirm-command "$(MAKE) --no-print-directory test-java-confirm" $(JAVA_SLOW_TEST_MODE)

# Streams one test-java phase live to the terminal while appending it to the
# retained log, records its wall time, and fails with the phase's own
# unchanged exit status.
java-test-phase:
	@$(JAVA_SLOW_TEST_GUARD) run --log $(JAVA_TEST_LOG) \
		--timing $(JAVA_SLOW_TEST_STATE)/phases.txt --phase $(JAVA_TEST_PHASE) -- \
		$(MAKE) --no-print-directory $(JAVA_TEST_PHASE)

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

# The single reduced-contention confirmation run for slow-test suspects.
test-java-confirm:
	$(MVN) $(MVN_FLAGS) \
		-Djunit.jupiter.execution.parallel.enabled=false \
		"-Dtest=$(JAVA_CONFIRM_TESTS)" \
		-Dprotos.surefire.reports=$(abspath $(JAVA_CONFIRM_REPORTS)) \
		test

test-java-stress:
	$(MVN) $(MVN_FLAGS) \
		-Djunit.jupiter.execution.parallel.enabled=false \
		-Dtest=$(JAVA_STRESS_TESTS) \
		test

check-local-range-index-pe:
	$(PYTHON) tools/test_java_local_range_pe_guard.py
	$(PYTHON) tools/java_local_range_pe_guard.py check \
		--source src/main/java \
		--baseline $(LOCAL_RANGE_PE_GUARD_BASELINE) \
		--reachability-baseline $(LOCAL_RANGE_PE_REACHABILITY_BASELINE) \
		--report $(LOCAL_RANGE_PE_GUARD_REPORT)

check-local-range-operands-pe:
	$(PYTHON) tools/test_java_local_range_operand_pe_guard.py
	$(PYTHON) tools/java_local_range_operand_pe_guard.py check \
		--source src/main/java \
		--baseline $(LOCAL_RANGE_OPERAND_PE_BASELINE) \
		--report $(LOCAL_RANGE_OPERAND_PE_REPORT)

check-local-accessor-pe:
	$(PYTHON) tools/test_java_local_accessor_pe_guard.py
	$(PYTHON) tools/java_local_accessor_pe_guard.py check \
		--source src/main/java \
		--baseline $(LOCAL_ACCESSOR_PE_BASELINE) \
		--report $(LOCAL_ACCESSOR_PE_REPORT)

check-bytecode-api-pe:
	$(PYTHON) tools/test_java_bytecode_api_pe_guard.py
	$(PYTHON) tools/java_bytecode_api_pe_guard.py check \
		--source src/main/java \
		--baseline $(BYTECODE_API_PE_BASELINE) \
		--report $(BYTECODE_API_PE_REPORT)

# Historical PERF030-F name, kept for compatibility.
test-local-range-pe-guard: check-local-range-index-pe

test-protos:
	$(MVN) $(MVN_FLAGS) package -DskipTests
	@start=$$(date +%s); \
	bin/protos test --jobs $(PROTOS_TEST_JOBS); \
	status=$$?; \
	end=$$(date +%s); \
	printf 'Protos tests total time: %s s\n' "$$((end - start))"; \
	exit $$status

# TEST009: deterministic static guards measured well below 60 s run first.
check: toolchain check-local-range-index-pe check-local-range-operands-pe check-local-accessor-pe check-bytecode-api-pe test

verify:
	$(MVN) $(MVN_FLAGS) clean verify

clean:
	$(MVN) $(MVN_FLAGS) clean

# DIST010: one clean revision -> Native + portable JVM + D064 + manifest.
# Building an artifact set never creates a tag, GitHub Release, or upload.
artifacts:
	$(PYTHON) dist/build_artifact_set.py

artifacts-verify:
	$(PYTHON) dist/build_artifact_set.py --verify target/artifact-set

# DIST010-B: explicit, demand-driven D064 publication from an already-built
# set. Never builds, regenerates D064, deletes, tags, or creates a release.
artifacts-publish-d064:
	$(PYTHON) dist/publish_d064_oci.py --artifact-set target/artifact-set

dist:
	$(PYTHON) dist/build_portable.py

dist-validate: dist
	$(SH) dist/validate_portable.sh $(DIST_VALIDATE_FLAGS)
