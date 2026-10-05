# Repository-local developer entry points. Keep policy in Maven and repository scripts.
.DEFAULT_GOAL := help

MVN ?= mvn
PYTHON ?= python3
SH ?= sh
MVN_FLAGS ?=
DIST_VALIDATE_FLAGS ?= --require-clean-source
JAVA_TEST_JOBS ?= 6
PROTOS_TEST_JOBS ?= 8

# DAP tests own Graal tooling state and are not safe in the class-parallel lane.
JAVA_SERIAL_TESTS := ProtosI026FDapBehaviorTest,ProtosTestToolPerf017AdmissionTest
JAVA_SERIAL_TEST_EXCLUDES := **/ProtosI026FDapBehaviorTest.java,**/ProtosTestToolPerf017AdmissionTest.java
JAVA_PARALLEL_EXCLUDES := $(JAVA_SERIAL_TEST_EXCLUDES)
JAVA_STRESS_TESTS := ProtosJsonParserStress
# TEST008: retained current-run log and slow-test guard for `make test-java`.
JAVA_TEST_LOG := target/test-java.log
JAVA_SUREFIRE_REPORTS := target/surefire-reports
JAVA_SLOW_TEST_GUARD := $(PYTHON) tools/java_slow_test_guard.py
JAVA_SLOW_TEST_BASELINE := tools/java_slow_test_baseline.txt
JAVA_SLOW_TEST_STATE := target/java-slow-test
JAVA_CONFIRM_TESTS ?=
# TEST008 follow-up: slow-test measurement is telemetry only. Slow-test
# policy, environment and measurement verdicts never own `make test`'s exit
# status. The real Java and Protos test phases remain fail-closed.
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
# TEST009-D: generated cached-dispatch BCI PE guard (static over generated Java,
# plus the dynamic real-compilation check of the exception-handler transition).
GENERATED_BYTECODE_BCI_PE_BASELINE := tools/java_generated_bytecode_bci_pe_baseline.json
GENERATED_BYTECODE_BCI_PE_REPORT := target/generated-bytecode-bci-pe-guard-report.json
GENERATED_BYTECODE_BCI_PE_CANDIDATE := target/generated-bytecode-bci-pe-candidate-baseline.json
GENERATED_BYTECODE_BCI_COMPILATION_REPORT := target/generated-bytecode-bci-compilation-report.json
# TEST009-F: strict Truffle compilation gate over the real Test Tool corpus
# (SYNC and BACKGROUND modes) and its manual textual diagnostic surface.
TRUFFLE_COMPILATION_DIR := target/truffle-compilation
TRUFFLE_COMPILATION_REPORT := $(TRUFFLE_COMPILATION_DIR)/gate-report.json
TRUFFLE_COMPILATION_DIAGNOSE_REPORT := $(TRUFFLE_COMPILATION_DIR)/diagnose-report.json
TRUFFLE_COMPILATION_TEST_ARGS ?= --jobs $(PROTOS_TEST_JOBS)
TRUFFLE_COMPILATION_TIMEOUT ?= 3600
# BUG016-B: concurrent per-Case diagnostic JVMs; independent of PROTOS_TEST_JOBS.
TRUFFLE_COMPILATION_SHARD_WORKERS ?= 1
# BUG016-B: each diagnose shard runs exactly one Case, so the Test Tool --jobs is not forwarded by default.
TRUFFLE_COMPILATION_DIAGNOSE_TEST_ARGS ?=

.PHONY: help toolchain compile build test test-java java-test-phase test-java-parallel test-java-serial test-java-confirm test-java-stress test-local-range-pe-guard check-local-range-index-pe check-local-range-operands-pe check-local-accessor-pe check-bytecode-api-pe check-generated-bytecode-bci-pe check-truffle-compilation diagnose-truffle-compilation test-protos check verify clean artifacts artifacts-verify artifacts-publish-d064 dist dist-validate

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
		'  make check-generated-bytecode-bci-pe  Generated cached-dispatch BCI PE guard:' \
		'                      package, static generated-Java topology check and' \
		'                      real-compilation check of the exception-handler path' \
		'  make check-truffle-compilation  Strict Truffle compilation gate: package, then run' \
		'                      the Test Tool corpus on the JVM with CompileImmediately,' \
		'                      ExitVM and performance warnings as errors, in SYNC and' \
		'                      BACKGROUND compilation modes (included in make check)' \
		'  make diagnose-truffle-compilation  Manual textual compilation diagnostics' \
		'                      (expansion, inlining, performance-warning traces) retained' \
		'                      under target/truffle-compilation; never changes product code' \
		'  make test-local-range-pe-guard  Compatibility alias of check-local-range-index-pe' \
		'  make test-protos    Build Protos and run the native Protos test suite' \
		'  make check          Run compilerability / PE bailout checks only:' \
		'                      toolchain, static PE guards, generated-dispatch BCI and' \
		'                      strict Truffle compilation; never runs make test' \
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
		'Overrides: MVN=... PYTHON=... SH=... MVN_FLAGS=... JAVA_TEST_JOBS=... PROTOS_TEST_JOBS=... DIST_VALIDATE_FLAGS=...' \
		'           TRUFFLE_COMPILATION_TEST_ARGS=... TRUFFLE_COMPILATION_TIMEOUT=...' \
		'           TRUFFLE_COMPILATION_SHARD_WORKERS=... TRUFFLE_COMPILATION_DIAGNOSE_TEST_ARGS=...'

toolchain:
	$(PYTHON) tools/verify_toolchain.py --mode check --scope development

compile:
	$(MVN) $(MVN_FLAGS) compile

build:
	$(MVN) $(MVN_FLAGS) clean package -DskipTests

test: test-java test-protos

# TEST008/PLAT047: slow-test controls and classification are telemetry only.
# Their setup and verdict cannot fail `make test`. The two real Java test phases
# below remain authoritative and preserve their own non-zero status unchanged.
test-java:
	-$(JAVA_SLOW_TEST_GUARD) reset --reports $(JAVA_SUREFIRE_REPORTS) --log $(JAVA_TEST_LOG) --state $(JAVA_SLOW_TEST_STATE)
	-$(JAVA_SLOW_TEST_GUARD) controls --state $(JAVA_SLOW_TEST_STATE) --jobs $(JAVA_TEST_JOBS)
	@$(MAKE) --no-print-directory java-test-phase JAVA_TEST_PHASE=test-java-parallel
	@$(MAKE) --no-print-directory java-test-phase JAVA_TEST_PHASE=test-java-serial
	-$(JAVA_SLOW_TEST_GUARD) check --reports $(JAVA_SUREFIRE_REPORTS) --baseline $(JAVA_SLOW_TEST_BASELINE) \
		--state $(JAVA_SLOW_TEST_STATE) --jobs $(JAVA_TEST_JOBS) --log $(JAVA_TEST_LOG) \
		--confirm-command "$(MAKE) --no-print-directory test-java-confirm" --advisory

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

# Packages first: the static guard reads the generated sources and the dynamic
# check runs the packaged CLI with real synchronous Truffle compilation.
check-generated-bytecode-bci-pe:
	$(MVN) $(MVN_FLAGS) package -DskipTests
	$(PYTHON) tools/test_java_generated_bytecode_bci_pe_guard.py
	$(PYTHON) tools/test_java_generated_bytecode_bci_compilation_check.py
	$(PYTHON) tools/java_generated_bytecode_bci_pe_guard.py check \
		--generated target/generated-sources \
		--baseline $(GENERATED_BYTECODE_BCI_PE_BASELINE) \
		--report $(GENERATED_BYTECODE_BCI_PE_REPORT) \
		--candidate $(GENERATED_BYTECODE_BCI_PE_CANDIDATE)
	$(PYTHON) tools/java_generated_bytecode_bci_compilation_check.py \
		--report $(GENERATED_BYTECODE_BCI_COMPILATION_REPORT)

# TEST009-F: packages once, then runs both strict modes; fail-closed.
check-truffle-compilation:
	$(MVN) $(MVN_FLAGS) package -DskipTests
	$(PYTHON) tools/test_truffle_compilation_gate.py
	$(PYTHON) tools/truffle_compilation_gate.py check \
		--timeout $(TRUFFLE_COMPILATION_TIMEOUT) \
		--artifacts $(TRUFFLE_COMPILATION_DIR) \
		--report $(TRUFFLE_COMPILATION_REPORT) \
		-- $(TRUFFLE_COMPILATION_TEST_ARGS)

# TEST009-F: manual escalation only; narrow TRUFFLE_COMPILATION_DIAGNOSE_TEST_ARGS to the
# failing selection, the traces are large.
diagnose-truffle-compilation:
	$(MVN) $(MVN_FLAGS) package -DskipTests
	$(PYTHON) tools/truffle_compilation_gate.py diagnose \
		--timeout $(TRUFFLE_COMPILATION_TIMEOUT) \
		--shard-workers $(TRUFFLE_COMPILATION_SHARD_WORKERS) \
		--artifacts $(TRUFFLE_COMPILATION_DIR) \
		--report $(TRUFFLE_COMPILATION_DIAGNOSE_REPORT) \
		-- $(TRUFFLE_COMPILATION_DIAGNOSE_TEST_ARGS)

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

# TEST009: compilerability validation is independent from functional tests.
# Cheap/static guards and the generated-dispatch BCI guard run first; the strict
# Truffle compilation gate is the final bailout authority. `make test` is never
# a prerequisite of `make check`.
check: toolchain check-local-range-index-pe check-local-range-operands-pe check-local-accessor-pe check-bytecode-api-pe check-generated-bytecode-bci-pe check-truffle-compilation

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
