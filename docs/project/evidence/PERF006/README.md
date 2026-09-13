# PERF006 immutable validation evidence

## PERF006-C3 — optimizer-enabled retained/full validation

Status: **CLOSED**

This evidence records the validation-only PERF006-C3 closure. It changes no
Protos implementation, specification, runtime architecture, dependency
authority, Graal/Truffle version, or implementation version.

The validated source revision already contains the published C3C full-suite
harness reconciliation, C3D P/Bytecode Context-projection closure, C3F PLAT030
release-suspension carrier correction, and C3E post-cutover full-suite harness
and Package Tool reconciliation. C3 does not reimplement those prerequisites;
it proves the complete retained and Maven test surfaces over their published
result.

```text
validated_source_revision=c15ac425fcc976e42b6d97638c5a890e97fdd035
implementation_version=0.2.492-SNAPSHOT
validated_at_utc=2026-09-13T13:50:06Z
optimizing_runtime=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
graal_truffle_version=25.3.4.1
warning_suppression=NO
c3d_required_ancestor=a1697479733b881789e694b46250dd310c9b9c42
c3c_required_ancestor=b6ce0154a0b6e26fc48e20afa8456abf48df2d81
c3f_required_ancestor=020a8206061f70f89f13fb298f47a37f000ce1b9
c3e_required_ancestor=c15ac425fcc976e42b6d97638c5a890e97fdd035
```

### Retained PERF006 validation

The complete retained Java test family selected by `ProtosPerf006*Test` ran
under Maven/Surefire with C1 proving the exact optimizing runtime.

```text
tests=267
failures=0
errors=0
skipped=0
surefire_reports=62
result=PASS
```

### Complete Maven validation

The complete `mvn test` suite then ran from the same immutable source revision.
The full run itself included the C1 runtime-identity assertion, so the green
suite is evidence under `HotSpotTruffleRuntime`, not merely under the fallback
Truffle runtime.

```text
tests=1913
failures=0
errors=0
skipped=0
surefire_reports=421
result=PASS
```

### Ordinary guest and self-hosted test-tool validation

After the full Maven suite:

- `mvn -DskipTests package` rebuilt the checkout artifact and canonical runtime
  plane;
- `bin/protos -e` executed a real guest program successfully through the
  checkout launcher;
- no fallback-runtime warning was observed on that guest execution; and
- `bin/protos test --jobs 2` passed as the repository's second-stage Protos Test
  Tool validation.

### Debt paid by C3

The deferred retained/full semantic test debt accumulated through PERF006-B,
PERF006-C1 and PERF006-C2 is paid for the validated source revision above.

C3 does **not** close PERF006-C or parent PERF006. The next bounded slice is
PERF006-C4, which owns final runtime-identity/warning closure across intended
ordinary surfaces. Final performance characterization remains PERF006-D.

## PERF006-C4 — runtime identity and fallback-warning closure

Status: **CLOSED**

C4 validates the final PLAT033 runtime-identity contract after C3 full semantic
validation. It changes no Protos implementation, specification, runtime
architecture, dependency authority, Graal/Truffle version, or implementation
version.

```text
validated_source_revision=428e46523e8fa0b3f0260b5a6e76c198725c041b
implementation_version=0.2.492-SNAPSHOT
validated_at_utc=2026-09-13T13:59:43Z
optimizing_runtime=com.oracle.truffle.runtime.hotspot.HotSpotTruffleRuntime
graal_truffle_version=25.3.4.1
warning_suppression=NO
truffle_fallback_warning=ABSENT_ALL_INTENDED_SURFACES
```

### Intended optimizer surfaces

```text
Maven/Surefire                         PASS exact HotSpotTruffleRuntime
checkout runtime plane                 PASS exact HotSpotTruffleRuntime
checkout ordinary guest execution      PASS no fallback warning
checkout Package Tool                  PASS no fallback warning
checkout Test Tool                     PASS no fallback warning
portable runtime plane                 PASS exact HotSpotTruffleRuntime
portable ordinary guest execution      PASS no fallback warning
portable Package Tool                  PASS no fallback warning
portable Test Tool                     PASS no fallback warning
checkout/dist runtime manifest         PASS byte-for-byte identity
```

The fallback-warning detector rejects both the historical
`No optimizing Truffle runtime found` form and the current Polyglot
interpreter-only/runtime-compilation warning family. Neither
`polyglot.engine.WarnInterpreterOnly=false` nor
`truffle.UseFallbackRuntime=true` is used.

JDK native-access and `sun.misc.Unsafe::objectFieldOffset` warnings are
classified separately as host-JDK/upstream runtime deprecation diagnostics.
They are not interpreter-fallback evidence and are not suppressed by C4.

### PERF006-C consequence

PERF006-C1, C2, C3 and C4 are now closed. The PLAT033 Candidate A-prime
runtime plane is proven across all intended ordinary JVM surfaces. PERF006-C is
therefore complete. Parent PERF006 remains open for the dedicated performance
evidence/closure track; no parent closure is claimed by C4.
