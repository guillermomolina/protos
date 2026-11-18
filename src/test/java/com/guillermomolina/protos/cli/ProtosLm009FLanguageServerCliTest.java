/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
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

    @Test
    @Timeout(10)
    void publicLanguageServerCommandAnswersDefinitionInsideAndJustAfterAReference()
            throws Exception {
        String uri = "file:///workspace/Slots.protos";
        String text = "x: 10\nx = 20\nprint(x)\n";
        ByteArrayOutputStream input = new ByteArrayOutputStream();
        input.write(frame(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                        + "\"params\":{\"processId\":null,"
                        + "\"rootUri\":null,\"capabilities\":{}}}"));
        input.write(frame(
                "{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}"));
        input.write(frame(didOpenRequest(uri, text)));
        // Read "x" in print(x) spans line 2 characters 6-7.
        input.write(frame(definitionRequest(2, uri, 2, 6)));
        input.write(frame(definitionRequest(3, uri, 2, 7)));
        // A capture proven by immediate invocation: "x" spans line 1
        // characters 7-8 and resolves to the SOURCE creation "x:".
        String captureUri = "file:///workspace/Capture.protos";
        input.write(frame(didOpenRequest(captureUri, "x: 1\n(() => x)()\n")));
        input.write(frame(definitionRequest(4, captureUri, 1, 7)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        int code =
                new ProtosCli()
                        .run(
                                new String[] {"language-server"},
                                new ByteArrayInputStream(input.toByteArray()),
                                new PrintStream(out, true, StandardCharsets.UTF_8),
                                new PrintStream(err, true, StandardCharsets.UTF_8));

        assertEquals(0, code);
        awaitContains(out, "\"id\":4");

        Map<Integer, JsonObject> responses = responses(out.toByteArray());
        for (int id : new int[] {2, 3}) {
            JsonObject response = responses.get(id);
            assertNotNull(response, out.toString(StandardCharsets.UTF_8));
            assertFalse(response.has("error"), response.toString());
            JsonArray result = response.getAsJsonArray("result");
            assertEquals(1, result.size(), response.toString());
            JsonObject location = result.get(0).getAsJsonObject();
            assertEquals(uri, location.get("uri").getAsString());
            JsonObject range = location.getAsJsonObject("range");
            assertPosition(range.getAsJsonObject("start"), 0, 0);
            assertPosition(range.getAsJsonObject("end"), 0, 1);
        }
        assertEquals(
                responses.get(2).get("result"),
                responses.get(3).get("result"));

        JsonObject captureResponse = responses.get(4);
        assertNotNull(captureResponse, out.toString(StandardCharsets.UTF_8));
        assertFalse(captureResponse.has("error"), captureResponse.toString());
        JsonArray captureResult = captureResponse.getAsJsonArray("result");
        assertEquals(1, captureResult.size(), captureResponse.toString());
        JsonObject captureLocation = captureResult.get(0).getAsJsonObject();
        assertEquals(captureUri, captureLocation.get("uri").getAsString());
        JsonObject captureRange = captureLocation.getAsJsonObject("range");
        assertPosition(captureRange.getAsJsonObject("start"), 0, 0);
        assertPosition(captureRange.getAsJsonObject("end"), 0, 1);
        assertTrue(
                err.toString(StandardCharsets.UTF_8).isBlank(),
                err.toString(StandardCharsets.UTF_8));
    }

    private static String didOpenRequest(String uri, String text) {
        JsonObject document = new JsonObject();
        document.addProperty("uri", uri);
        document.addProperty("languageId", "protos");
        document.addProperty("version", 1);
        document.addProperty("text", text);

        JsonObject params = new JsonObject();
        params.add("textDocument", document);

        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("method", "textDocument/didOpen");
        request.add("params", params);

        return request.toString();
    }

    private static String definitionRequest(int id, String uri, int line, int character) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ","
                + "\"method\":\"textDocument/definition\","
                + "\"params\":{\"textDocument\":{\"uri\":\"" + uri + "\"},"
                + "\"position\":{\"line\":" + line + ",\"character\":" + character + "}}}";
    }

    private static void assertPosition(JsonObject position, int line, int character) {
        assertEquals(line, position.get("line").getAsInt(), position.toString());
        assertEquals(character, position.get("character").getAsInt(), position.toString());
    }

    private static Map<Integer, JsonObject> responses(byte[] output) {
        Map<Integer, JsonObject> responses = new HashMap<>();
        String header = "Content-Length: ";
        int index = 0;
        while (index < output.length) {
            String rest = new String(
                    output, index, output.length - index, StandardCharsets.US_ASCII);
            assertTrue(rest.startsWith(header), rest);
            int headerEnd = rest.indexOf("\r\n\r\n");
            assertTrue(headerEnd > 0, rest);
            int length = Integer.parseInt(rest.substring(header.length(), headerEnd).trim());
            int bodyStart = index + headerEnd + 4;
            JsonObject message = JsonParser.parseString(
                    new String(output, bodyStart, length, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (message.has("id") && !message.has("method")) {
                responses.put(message.get("id").getAsInt(), message);
            }
            index = bodyStart + length;
        }
        return responses;
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
