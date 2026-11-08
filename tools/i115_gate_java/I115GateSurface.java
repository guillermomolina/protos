/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT at the repository root.
 */
package com.guillermomolina.protos.gate;

import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/** An untimed, deterministic, source-backed Polyglot Value.execute() diagnostic. */
public final class I115GateSurface {
    private I115GateSurface() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "usage: <core-root> <source> <case-id> <expected-integer> <calls>");
        }
        Path core = Path.of(args[0]).toAbsolutePath().normalize();
        Path sourcePath = Path.of(args[1]).toAbsolutePath().normalize();
        String caseId = args[2];
        if (!caseId.matches("[a-z][a-z0-9-]*")) {
            throw new IllegalArgumentException("Invalid case ID");
        }
        BigInteger expected = new BigInteger(args[3]);
        int calls = Integer.parseInt(args[4]);
        if (calls <= 0 || calls > 100000) {
            throw new IllegalArgumentException("Invalid call count: " + calls);
        }
        Source source = Source.newBuilder("protos", Files.readString(sourcePath),
                sourcePath.toString())
                // Stable across local checkout locations. The gate also pairs roots by
                // semantic source span, not by compiler graph size or a physical id.
                .uri(URI.create("i115:/cases/" + caseId + ".protos"))
                .build();
        try (Context context = Context.newBuilder("protos")
                .allowExperimentalOptions(true)
                .option("protos.CoreRoot", core.toString())
                .build()) {
            context.eval(source);
            Value run = context.getBindings("protos").getMember("run");
            if (run == null || !run.canExecute()) {
                throw new IllegalStateException("Missing executable run");
            }
            for (int i = 0; i < calls; i++) {
                Value actual = run.execute();
                if (!actual.fitsInBigInteger()
                        || !expected.equals(actual.asBigInteger())) {
                    throw new IllegalStateException("Incorrect result at call " + i
                            + ": expected " + expected + ", got " + actual);
                }
            }
            System.out.println("result=" + expected);
            System.out.println("calls=" + calls);
        }
    }
}
