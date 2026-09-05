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

package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.guillermomolina.protos.execution.ProtosCoreBootstrap;
import com.guillermomolina.protos.execution.ProtosStandardBytesProtocol;
import com.guillermomolina.protos.execution.ProtosStandardProcessStreamProtocol;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ProtosProcessStandardStreamEncodingTest {
    private static final Path CORE = Path.of("protos", "lib", "core");

    @Test
    void independentlyAvailableStreamsOwnStableBootstrapEncodingAssociations()
            throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process = process();
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");
        ProtosEncodingValue latin1 = encoding(prelude, "Latin1");

        establishStreams(process, readableBackend(), null, writableBackend());
        process.establishStandardStreamEncodingsForRuntime(utf8, null, latin1);

        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.AVAILABLE,
                process.stdinEncodingStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.UNAVAILABLE,
                process.stdoutEncodingStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.AVAILABLE,
                process.stderrEncodingStateForRuntime());
        assertSame(utf8, process.stdinEncodingForRuntime().orElseThrow());
        assertTrue(process.stdoutEncodingForRuntime().isEmpty());
        assertSame(latin1, process.stderrEncodingForRuntime().orElseThrow());
        assertSame(
                process.stdinEncodingForRuntime().orElseThrow(),
                process.stdinEncodingForRuntime().orElseThrow());
    }

    @Test
    void hostProvidedDescriptorMayBeAssociatedWithAvailableStream() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process = process();
        ProtosEncodingValue host =
                ProtosEncodingValue.hostProvidedForRuntime(
                        prelude.encodingPrototype(),
                        new ProtosHostEncodingTestCodec());

        establishStreams(process, null, writableBackend(), null);
        process.establishStandardStreamEncodingsForRuntime(null, host, null);

        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.AVAILABLE,
                process.stdoutEncodingStateForRuntime());
        assertSame(host, process.stdoutEncodingForRuntime().orElseThrow());
        assertFalse(host.isPortableForRuntime());
    }

    @Test
    void availabilityMismatchBecomesStableInvalidBootstrapState() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosProcessRuntime process = process();
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        establishStreams(process, readableBackend(), null, null);
        process.establishStandardStreamEncodingsForRuntime(null, utf8, null);

        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                process.stdinEncodingStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                process.stdoutEncodingStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.UNAVAILABLE,
                process.stderrEncodingStateForRuntime());
        assertTrue(process.stdinEncodingForRuntime().isEmpty());
        assertTrue(process.stdoutEncodingForRuntime().isEmpty());
        assertTrue(process.stderrEncodingForRuntime().isEmpty());

        assertThrows(
                IllegalStateException.class,
                () -> process.establishStandardStreamEncodingsForRuntime(utf8, null, null));

        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                process.stdinEncodingStateForRuntime());
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.INVALID,
                process.stdoutEncodingStateForRuntime());

        // D2 does not redefine D1's independently established byte availability.
        assertTrue(process.stdinForRuntime().isPresent());
        assertTrue(process.stdoutForRuntime().isEmpty());
    }

    @Test
    void associationRequiresEstablishedStreamsAndRunningProcess() throws Exception {
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE);
        ProtosEncodingValue utf8 = encoding(prelude, "UTF8");

        ProtosProcessRuntime beforeStreams = process();
        assertThrows(
                IllegalStateException.class,
                () -> beforeStreams.establishStandardStreamEncodingsForRuntime(utf8, null, null));

        ProtosProcessRuntime terminated = process();
        establishStreams(terminated, readableBackend(), null, null);
        assertTrue(terminated.requestTerminationForRuntime());
        assertThrows(
                IllegalStateException.class,
                () -> terminated.establishStandardStreamEncodingsForRuntime(utf8, null, null));
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.UNESTABLISHED,
                terminated.stdinEncodingStateForRuntime());
    }

    @Test
    void encodingValueAccessorsRequireEstablishedAssociationState() throws Exception {
        ProtosProcessRuntime process = process();
        establishStreams(process, null, null, null);

        assertThrows(IllegalStateException.class, process::stdinEncodingForRuntime);
        assertThrows(IllegalStateException.class, process::stdoutEncodingForRuntime);
        assertThrows(IllegalStateException.class, process::stderrEncodingForRuntime);
        assertEquals(
                ProtosProcessRuntime.StandardStreamEncodingState.UNESTABLISHED,
                process.stdinEncodingStateForRuntime());
    }

    private static ProtosProcessRuntime process() {
        return new ProtosProcessRuntime(
                new ProtosObjectValue(ProtosObjectValue.rootObject()).freeze());
    }

    private static ProtosEncodingValue encoding(ProtosPrelude prelude, String name) {
        return assertInstanceOf(
                ProtosEncodingValue.class,
                prelude.encodingPrototype().readLocalSlot(name).orElseThrow());
    }

    private static void establishStreams(
            ProtosProcessRuntime process,
            ProtosProcessStandardStreamBinding.ReadableBackend stdin,
            ProtosProcessStandardStreamBinding.WritableBackend stdout,
            ProtosProcessStandardStreamBinding.WritableBackend stderr) {
        ProtosObjectValue bytesPrototype =
                new ProtosObjectValue(ProtosObjectValue.rootObject());
        ProtosStandardBytesProtocol.install(bytesPrototype);
        process.establishStandardStreamsForRuntime(
                ProtosStandardProcessStreamProtocol.createReadablePrototype(),
                ProtosStandardProcessStreamProtocol.createWritablePrototype(),
                bytesPrototype,
                stdin,
                stdout,
                stderr);
    }

    private static ProtosProcessStandardStreamBinding.ReadableBackend readableBackend() {
        return (maxBytes, completion) -> () -> {};
    }

    private static ProtosProcessStandardStreamBinding.WritableBackend writableBackend() {
        return (bytes, completion) -> () -> {};
    }
}
