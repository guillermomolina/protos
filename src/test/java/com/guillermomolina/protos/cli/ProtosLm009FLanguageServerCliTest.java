/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class ProtosLm009FLanguageServerCliTest {

    @Test
    @Timeout(10)
    void publicLanguageServerCommandRoutesOnlyLspFramingOnStdout() throws Exception {
        String initialize =
                "{\"jsonrpc\":\"2.0\",\"id\":1,"
                        + "\"method\":\"initialize\","
                        + "\"params\":{\"processId\":null,"
                        + "\"rootUri\":null,\"capabilities\":{}}}";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                new String[] {"language-server"},
                                new ByteArrayInputStream(frame(initialize)),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code);
        awaitContains(out, "\"id\":1");

        String protocol = out.toString(StandardCharsets.UTF_8);
        assertTrue(protocol.startsWith("Content-Length:"), protocol);
        assertTrue(protocol.contains("\"jsonrpc\":\"2.0\""), protocol);
        assertTrue(protocol.contains("\"capabilities\""), protocol);
        assertTrue(protocol.contains("\"textDocumentSync\""), protocol);
        assertFalse(protocol.contains("Usage:"), protocol);
        assertFalse(protocol.contains("Protos development"), protocol);
        assertTrue(
                err.toString(StandardCharsets.UTF_8).isBlank(),
                err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void publicLanguageServerCommandRejectsUnexpectedArgumentsBeforeProtocolStartup() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                new String[] {"language-server", "--unexpected"},
                                InputStream.nullInputStream(),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertNotEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).isBlank());
        assertTrue(
                err.toString(StandardCharsets.UTF_8)
                        .contains("language-server accepts no arguments"));
    }

    @Test
    void helpPublishesTheRatifiedLanguageServerRoleCommand() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                new String[] {"--help"},
                                InputStream.nullInputStream(),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("protos language-server"));
        assertTrue(
                out.toString(StandardCharsets.UTF_8)
                        .contains("standard LSP over stdin/stdout"));
        assertTrue(err.toString(StandardCharsets.UTF_8).isBlank());
    }

    private static byte[] frame(String payload) throws IOException {
        byte[] body = payload.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(
                ("Content-Length: " + body.length + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
        bytes.write(body);
        return bytes.toByteArray();
    }

    private static void awaitContains(ByteArrayOutputStream output, String expected)
            throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (output.toString(StandardCharsets.UTF_8).contains(expected)) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        fail(
                "Timed out waiting for LSP response marker "
                        + expected
                        + ": "
                        + output.toString(StandardCharsets.UTF_8));
    }
}
