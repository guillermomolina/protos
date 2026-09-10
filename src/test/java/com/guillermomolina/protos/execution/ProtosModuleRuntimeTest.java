/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */

package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosClosureValue;
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
    void coreImportFacilityIsSourceBackedIdentityWithOnlyNativeCallBridge()
            throws Exception {
        MemoryResolver resolver = new MemoryResolver();
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosObjectValue facility =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        prelude.bindings().readLocalSlot("import").orElseThrow());

        assertSame(ProtosObjectValue.rootObject(), facility.parent().orElseThrow());
        assertTrue(facility.isFrozen());
        assertEquals(java.util.Set.of("call"), facility.localSlotsSnapshot().keySet());
        ProtosClosureValue call =
                assertInstanceOf(
                        ProtosClosureValue.class,
                        facility.readLocalSlot("call").orElseThrow());
        assertTrue(call.nativeBody().isPresent());
        assertNull(call.definition());

        ProtosObjectValue supplied =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosObjectValue installed =
                ProtosStandardImportProtocol.installImportFacility(
                        supplied, new ProtosModuleRuntime(resolver));
        assertSame(supplied, installed);
        assertTrue(installed.isFrozen());
        assertEquals(java.util.Set.of("call"), installed.localSlotsSnapshot().keySet());
    }

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
    void moduleInstanceIsContextWithExplicitNamespaceAndNoImporterInheritance()
            throws Exception {
        MemoryResolver resolver =
                new MemoryResolver()
                        .module("leaf", "secret: 9")
                        .module(
                                "mid",
                                "dep: import(\"leaf\")\n"
                                        + "notInherited: Error.handle(() => importerOnly, "
                                        + "(caught) => caught.parent() === SlotNotFound)");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                "importerOnly: 7\n"
                                        + "mid: import(\"mid\")\n"
                                        + "transitiveMissing: Error.handle(() => mid.secret, "
                                        + "(caught) => caught.parent() === SlotNotFound)\n"
                                        + "(mid.parent() === Context).and() {\n"
                                        + "    (mid.dep.secret == 9).and() {\n"
                                        + "        mid.notInherited.and() { transitiveMissing }\n"
                                        + "    }\n"
                                        + "}")
                        .call(actor);

        assertSame(ProtosBooleanValue.TRUE, result);
        assertEquals(java.util.List.of("<root>", "mid"), resolver.resolvedFrom);
    }

    @Test
    void exactSpecifierTextAndCanonicalIdentityAreDistinctBoundaries() throws Exception {
        MemoryResolver resolver =
                new MemoryResolver()
                        .module("empty", "value: 2")
                        .alias("", "empty")
                        .module("a", "value: 1")
                        .alias("β/../A", "a")
                        .alias("alias-a", "a")
                        .module("b", "value: 1");
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        ProtosActivation actor = prelude.newModuleActivation();

        Object result =
                new ProtosSourceCompiler()
                        .compile(
                                "empty: import(\"\")\n"
                                        + "a: import(\"β/../A\")\n"
                                        + "same: import(\"alias-a\")\n"
                                        + "b: import(\"b\")\n"
                                        + "(empty.value == 2).and() {\n"
                                        + "    (a === same).and() { a !== b }\n"
                                        + "}")
                        .call(actor);

        assertSame(ProtosBooleanValue.TRUE, result);
        assertEquals(
                java.util.List.of("", "β/../A", "alias-a", "b"),
                resolver.resolvedSpecifiers);
        assertEquals(1, resolver.loads("empty"));
        assertEquals(1, resolver.loads("a"));
        assertEquals(1, resolver.loads("b"));
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
            public ProtosModuleSource loadSource(ProtosModuleKey key) {
                loadCounts.merge(key.canonicalId(), 1, Integer::sum);
                return ProtosModuleSource.fromCharacters(key, attempt.getAndIncrement() == 0 ? "broken: (" : "ok: 42");
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
            @Override public ProtosModuleSource loadSource(ProtosModuleKey key) { return ProtosModuleSource.fromCharacters(key, ""); }
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
        final java.util.List<String> resolvedSpecifiers = new java.util.ArrayList<>();
        final java.util.List<String> resolvedFrom = new java.util.ArrayList<>();

        MemoryResolver module(String key, String source) { aliases.put(key, key); sources.put(key, source); return this; }
        MemoryResolver alias(String spelling, String key) { aliases.put(spelling, key); return this; }
        int loads(String key) { return loadCounts.getOrDefault(key, 0); }

        @Override public ProtosModuleKey resolve(String exactSpecifier, Optional<ProtosModuleKey> importingModule) throws Exception {
            resolveCalls.incrementAndGet();
            resolvedSpecifiers.add(exactSpecifier);
            resolvedFrom.add(importingModule.map(ProtosModuleKey::canonicalId).orElse("<root>"));
            String key = aliases.get(exactSpecifier);
            if (key == null) throw new java.io.IOException("not found");
            return new ProtosModuleKey(key);
        }

        @Override public ProtosModuleSource loadSource(ProtosModuleKey key) throws Exception {
            loadCounts.merge(key.canonicalId(), 1, Integer::sum);
            String source = sources.get(key.canonicalId());
            if (source == null) throw new java.io.IOException("not found");
            return ProtosModuleSource.fromCharacters(key, source);
        }
    }
}
