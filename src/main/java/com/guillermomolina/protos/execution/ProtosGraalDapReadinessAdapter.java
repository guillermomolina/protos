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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Version-bounded adapter from GraalVM 25.3.4.1 DAP startup output to a stable Protos endpoint.
 *
 * <p>PLAT018 deliberately keeps Graal-specific readiness discovery behind this boundary. The
 * public D060 readiness record is produced later by the CLI and must never parse this text itself.
 */
final class ProtosGraalDapReadinessAdapter extends OutputStream {
    private static final String STARTED_PREFIX =
            "[Graal DAP] Starting server and listening on ";

    private final OutputStream diagnostics;
    private final ByteArrayOutputStream pendingLine = new ByteArrayOutputStream();
    private ProtosPolyglotRuntimeHost.DebugEndpoint endpoint;
    private RuntimeException readinessFailure;

    ProtosGraalDapReadinessAdapter(OutputStream diagnostics) {
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
    }

    @Override
    public synchronized void write(int value) throws IOException {
        acceptByte(value & 0xff);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        for (int index = offset; index < offset + length; index++) {
            acceptByte(bytes[index] & 0xff);
        }
    }

    @Override
    public synchronized void flush() throws IOException {
        diagnostics.flush();
    }

    synchronized ProtosPolyglotRuntimeHost.DebugEndpoint requireEndpoint() {
        if (readinessFailure != null) {
            throw new IllegalStateException(
                    "GraalVM DAP readiness output is incompatible with the retained adapter",
                    readinessFailure);
        }
        if (endpoint == null) {
            throw new IllegalStateException(
                    "GraalVM DAP did not publish a bound endpoint during RuntimeHost startup");
        }
        return endpoint;
    }

    private void acceptByte(int value) throws IOException {
        if (value == '\n') {
            completeLine();
        } else {
            pendingLine.write(value);
        }
    }

    private void completeLine() throws IOException {
        byte[] bytes = pendingLine.toByteArray();
        pendingLine.reset();

        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
            length--;
        }
        String line = new String(bytes, 0, length, StandardCharsets.UTF_8);

        if (line.startsWith(STARTED_PREFIX)) {
            if (endpoint != null) {
                readinessFailure =
                        new IllegalStateException("GraalVM DAP published readiness more than once");
                return;
            }
            try {
                endpoint = parseEndpoint(line.substring(STARTED_PREFIX.length()));
            } catch (RuntimeException failure) {
                readinessFailure = failure;
            }
            return;
        }

        if (length != 0) {
            diagnostics.write(bytes, 0, length);
            diagnostics.write('\n');
            diagnostics.flush();
        }
    }

    private static ProtosPolyglotRuntimeHost.DebugEndpoint parseEndpoint(String rawAddress) {
        String address = rawAddress.trim();
        int slash = address.lastIndexOf('/');
        if (slash >= 0) {
            address = address.substring(slash + 1);
        }

        String host;
        String portText;
        if (address.startsWith("[")) {
            int bracket = address.indexOf(']');
            if (bracket <= 1 || bracket + 1 >= address.length() || address.charAt(bracket + 1) != ':') {
                throw new IllegalArgumentException("Malformed bracketed Graal DAP address: " + rawAddress);
            }
            host = address.substring(1, bracket);
            portText = address.substring(bracket + 2);
        } else {
            int colon = address.lastIndexOf(':');
            if (colon <= 0 || colon + 1 >= address.length()) {
                throw new IllegalArgumentException("Malformed Graal DAP address: " + rawAddress);
            }
            host = address.substring(0, colon);
            portText = address.substring(colon + 1);
        }

        if (!host.matches("[0-9A-Fa-f:.%]+")) {
            throw new IllegalArgumentException(
                    "Graal DAP readiness did not contain a numeric address: " + host);
        }

        final InetAddress addressValue;
        try {
            addressValue = InetAddress.getByName(host);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid Graal DAP host address: " + host, failure);
        }
        if (!addressValue.isLoopbackAddress()) {
            throw new IllegalArgumentException(
                    "Graal DAP readiness escaped the PLAT018 loopback boundary: " + host);
        }

        final int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid Graal DAP port: " + portText, failure);
        }

        return new ProtosPolyglotRuntimeHost.DebugEndpoint(addressValue.getHostAddress(), port);
    }
}
