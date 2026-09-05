/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosIntegerValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProtosModuleRuntimeTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void semanticStringImportsAndStringLikeObjectIsRejectedBeforeResolver() throws Exception {
        MemoryResolver resolver = new MemoryResolver().module("m", "value: 7");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();

        Object module = new ProtosSourceCompiler().compile("import(\"m\")").call(actor);
        assertInstanceOf(ProtosObjectValue.class, module);
        assertEquals(1, resolver.resolveCalls.get());

        assertThrows(ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("fake: String {}\nimport(fake)").call(actor));
        assertEquals(1, resolver.resolveCalls.get(), "invalid semantic domain must fail before resolution");
    }

    @Test
    void canonicalKeyCachesBeforeExecutionSupportsCyclesAndSingleEvaluation() throws Exception {
        MemoryResolver resolver = new MemoryResolver()
                .alias("alias-a", "a")
                .module("a", "before: 10\nb: import(\"b\")\naSeenByB: b.aBefore\nafter: 20")
                .module("b", "a: import(\"a\")\naBefore: a.before");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        ProtosObjectValue a1 = (ProtosObjectValue) compiler.compile("import(\"a\")").call(actor);
        ProtosObjectValue a2 = (ProtosObjectValue) compiler.compile("import(\"alias-a\")").call(actor);

        assertSame(a1, a2, "distinct spellings resolving to one ModuleKey must share one Actor-local instance");
        assertEquals(1, resolver.loads("a"), "A must execute/load once even through A -> B -> A");
        assertEquals(1, resolver.loads("b"));
        assertEquals(java.math.BigInteger.TEN, ((ProtosIntegerValue) a1.readLocalSlot("aSeenByB").orElseThrow()).value());
        assertTrue(a1.hasLocalSlot("after"));
    }

    @Test
    void cachesAreActorLocal() throws Exception {
        MemoryResolver resolver = new MemoryResolver().module("m", "value: 1");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();
        ProtosActivation actorA = prelude.newModuleActivation();
        ProtosActivation actorB = prelude.newModuleActivation();

        Object a1 = compiler.compile("import(\"m\")").call(actorA);
        Object a2 = compiler.compile("import(\"m\")").call(actorA);
        Object b1 = compiler.compile("import(\"m\")").call(actorB);

        assertSame(a1, a2);
        assertNotSame(a1, b1);
        assertEquals(2, resolver.loads("m"));
    }

    @Test
    void failedInitializationIsEvictedAndRetryCreatesFreshInstance() throws Exception {
        AtomicInteger attempt = new AtomicInteger();
        MemoryResolver resolver = new MemoryResolver() {
            @Override
            public String loadSource(ProtosModuleKey key) {
                loadCounts.merge(key.canonicalId(), 1, Integer::sum);
                return attempt.getAndIncrement() == 0 ? "broken: (" : "ok: 42";
            }
        }.module("retry", "ignored");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();
        ProtosSourceCompiler compiler = new ProtosSourceCompiler();

        assertThrows(ProtosSignalException.class, () -> compiler.compile("import(\"retry\")").call(actor));
        ProtosObjectValue recovered = (ProtosObjectValue) compiler.compile("import(\"retry\")").call(actor);
        assertTrue(recovered.hasLocalSlot("ok"));
        assertEquals(2, resolver.loads("retry"), "retry must re-load/re-evaluate after eviction");
    }

    @Test
    void resolverAndSourceFailuresBecomeCoreErrorsNotHostExceptions() throws Exception {
        ProtosModuleResolver resolver = new ProtosModuleResolver() {
            @Override public ProtosModuleKey resolve(String s, Optional<ProtosModuleKey> from) throws Exception {
                throw new java.io.IOException("host detail");
            }
            @Override public String loadSource(ProtosModuleKey key) { return ""; }
        };
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        assertThrows(ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile("import(\"x\")").call(prelude.newModuleActivation()));
    }

    static class MemoryResolver implements ProtosModuleResolver {
        final Map<String, String> aliases = new HashMap<>();
        final Map<String, String> sources = new HashMap<>();
        final Map<String, Integer> loadCounts = new HashMap<>();
        final AtomicInteger resolveCalls = new AtomicInteger();

        MemoryResolver module(String key, String source) { aliases.put(key, key); sources.put(key, source); return this; }
        MemoryResolver alias(String spelling, String key) { aliases.put(spelling, key); return this; }
        int loads(String key) { return loadCounts.getOrDefault(key, 0); }

        @Override public ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception {
            resolveCalls.incrementAndGet();
            String key = aliases.get(exactSpecifier);
            if (key == null) throw new java.io.IOException("not found");
            return new ProtosModuleKey(key);
        }

        @Override public String loadSource(ProtosModuleKey key) throws Exception {
            loadCounts.merge(key.canonicalId(), 1, Integer::sum);
            String source = sources.get(key.canonicalId());
            if (source == null) throw new java.io.IOException("not found");
            return source;
        }
    }
}
