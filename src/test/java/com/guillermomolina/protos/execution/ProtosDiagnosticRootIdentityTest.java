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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oracle.truffle.api.source.Source;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * TEST009-V focal coverage of the diagnostic stable semantic-root identity: selector
 * determinism and distinctness, fail-closed naming without metadata, catalog text, and
 * inactivity by default.
 */
final class ProtosDiagnosticRootIdentityTest {
    private static final Pattern SELECTOR = Pattern.compile("protos-root:[0-9a-f]{16}");
    private static final String TEXT = "a: () => { 1 }\nb: () => { 2 }\n";

    private static Source source(String name) {
        return Source.newBuilder(ProtosLanguage.ID, TEXT, name).build();
    }

    private static ProtosDiagnosticRootIdentity.Span closure(Source source, int start, int length) {
        return new ProtosDiagnosticRootIdentity.Span(
                source, start, length, ProtosDiagnosticRootIdentity.CLOSURE);
    }

    @Test
    void inactiveByDefaultSoOrdinaryRootNamingIsUnchanged() {
        assertFalse(ProtosDiagnosticRootIdentity.ENABLED);
        assertFalse(ProtosDiagnosticRootIdentity.isEnabled(null));
        assertFalse(ProtosDiagnosticRootIdentity.isEnabled("false"));
        assertFalse(ProtosDiagnosticRootIdentity.isEnabled("TRUE"));
        assertTrue(ProtosDiagnosticRootIdentity.isEnabled("true"));
    }

    @Test
    void selectorIsFixedWidthAndAddressFree() {
        ProtosDiagnosticRootIdentity.Span span = closure(source("dir/a.protos"), 3, 11);
        assertTrue(SELECTOR.matcher(ProtosDiagnosticRootIdentity.selector(span)).matches());
        String name = ProtosDiagnosticRootIdentity.targetName(span);
        assertFalse(name.contains("@"), name);
        assertFalse(name.contains(" "), name);
        assertTrue(name.startsWith(ProtosDiagnosticRootIdentity.selector(span) + "[CLOSURE|"), name);
        assertTrue(name.endsWith("|3+11]"), name);
    }

    @Test
    void sameStableMetadataGivesTheSameSelector() {
        ProtosDiagnosticRootIdentity.Span first = closure(source("dir/a.protos"), 3, 11);
        ProtosDiagnosticRootIdentity.Span second = closure(source("dir/a.protos"), 3, 11);
        assertEquals(ProtosDiagnosticRootIdentity.selector(first),
                ProtosDiagnosticRootIdentity.selector(second));
        assertEquals(ProtosDiagnosticRootIdentity.targetName(first),
                ProtosDiagnosticRootIdentity.targetName(second));
        assertEquals(ProtosDiagnosticRootIdentity.catalogLine(first),
                ProtosDiagnosticRootIdentity.catalogLine(second));
    }

    @Test
    void distinctKindSourceOrSpanDoNotAlias() {
        Source a = source("dir/a.protos");
        ProtosDiagnosticRootIdentity.Span base = closure(a, 3, 11);
        List<ProtosDiagnosticRootIdentity.Span> others = List.of(
                new ProtosDiagnosticRootIdentity.Span(a, 3, 11, ProtosDiagnosticRootIdentity.TOP_LEVEL),
                closure(source("dir/other.protos"), 3, 11),
                closure(a, 13, 11),
                closure(a, 3, 110));
        String selector = ProtosDiagnosticRootIdentity.selector(base);
        for (ProtosDiagnosticRootIdentity.Span other : others) {
            String otherSelector = ProtosDiagnosticRootIdentity.selector(other);
            assertNotEquals(selector, otherSelector, other.toString());
            // CompileOnly matches by substring: no selector may occur in another root's name.
            assertFalse(ProtosDiagnosticRootIdentity.targetName(other).contains(selector));
            assertFalse(ProtosDiagnosticRootIdentity.targetName(base).contains(otherSelector));
        }
    }

    @Test
    void missingStableMetadataFailsClosed() {
        String name = ProtosDiagnosticRootIdentity.targetName(null);
        assertEquals(ProtosDiagnosticRootIdentity.UNAVAILABLE, name);
        assertFalse(SELECTOR.matcher(name).lookingAt(), name);
        assertFalse(name.contains("@"), name);
    }

    @Test
    void catalogLineCarriesSelectorAndHumanReadableMetadata() {
        ProtosDiagnosticRootIdentity.Span span = closure(source("dir/a.protos"), 3, 11);
        String line = ProtosDiagnosticRootIdentity.catalogLine(span);
        assertEquals(ProtosDiagnosticRootIdentity.CATALOG_PREFIX + "selector="
                + ProtosDiagnosticRootIdentity.selector(span) + " kind=CLOSURE source="
                + span.sourceUri() + " start=3 length=11", line);
        assertFalse(line.contains("@"), line);
    }
}
