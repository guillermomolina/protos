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

import com.guillermomolina.protos.execution.ProtosEmbeddedModules;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/**
 * PLAT054-3C JAR-level probe, compiled and run by {@code smoke_polyglot_embedding.sh} against the
 * distributed {@code lib/protos.jar} from outside the checkout. Each mode prints one PASS marker or
 * exits non-zero.
 *
 * <ul>
 *   <li>{@code packaged}: option-free Context, the public example, a {@code std:} import, two
 *       Contexts, and absence of the home-only marker module (Core is not read from a home).
 *   <li>{@code home}: run with a language home whose library adds the marker module; importing it
 *       proves the home keeps precedence over the packaged Core.
 *   <li>{@code override <core>}: a valid {@code protos.CoreRoot} wins over the packaged Core.
 *   <li>{@code invalid-override <path>}: an invalid override fails without fallback.
 *   <li>{@code application-modules}: PLAT055 {@link ProtosEmbeddedModules} from an external
 *       application: an {@code app:} module imported by the main source and spawned as an Actor,
 *       with no Filesystem or Network authority implied.
 * </ul>
 */
public final class Plat054EmbeddingProbe {
    private static final String MARKER_IMPORT = "m: import(\"std:plat054probe/Marker\")\nm.origin";

    private Plat054EmbeddingProbe() {}

    public static void main(String[] args) {
        switch (args[0]) {
            case "packaged" -> packaged();
            case "home" -> expectMarker(Context.newBuilder("protos"), "home");
            case "override" ->
                    expectMarker(
                            Context.newBuilder("protos").option("protos.CoreRoot", args[1]),
                            "override");
            case "invalid-override" -> invalidOverride(args[1]);
            case "application-modules" -> applicationModules();
            default -> fail("unknown mode " + args[0]);
        }
    }

    private static void packaged() {
        try (Context context = Context.newBuilder("protos").build()) {
            if (!context.getBindings("protos").getMemberKeys().isEmpty()) {
                fail("bindings before eval are not empty");
            }
            context.eval("protos", "truffleRun: () => { 42 }\n");
            Value run = context.getBindings("protos").getMember("truffleRun");
            require(run.execute().asInt() == 42, "truffleRun() != 42");
            require(run.execute().asInt() == 42, "second truffleRun() != 42");
            Value bindings = context.getBindings("protos");
            require(!bindings.hasMember("filesystem"), "default Filesystem authority granted");
            require(!bindings.hasMember("network"), "default Network authority granted");

            context.eval("protos", "a: import(\"std:test/Assertions\")\n0");
            require(
                    context.getBindings("protos").hasMember("a"),
                    "std:test/Assertions not imported");
            try {
                context.eval("protos", MARKER_IMPORT);
                fail("home-only marker module resolved from the packaged Core");
            } catch (PolyglotException expected) {
                // The packaged Core has no marker module: Core did not come from a home.
            }
        }
        try (Context left = Context.newBuilder("protos").build();
                Context right = Context.newBuilder("protos").build()) {
            require(left.eval("protos", "1").asInt() == 1, "left Context");
            require(right.eval("protos", "2").asInt() == 2, "right Context");
        }
        System.out.println("PLAT054_PACKAGED: PASS");
    }

    private static void expectMarker(Context.Builder builder, String label) {
        try (Context context = builder.build()) {
            String origin = context.eval("protos", MARKER_IMPORT).asString();
            require("marker".equals(origin), label + " marker module origin " + origin);
        }
        System.out.println("PLAT054_" + label.toUpperCase() + ": PASS");
    }

    private static void invalidOverride(String path) {
        try (Context context = Context.newBuilder("protos").option("protos.CoreRoot", path).build()) {
            try {
                context.eval("protos", "1");
                fail("invalid protos.CoreRoot fell back to another Core");
            } catch (PolyglotException expected) {
                require(
                        expected.getMessage().contains("protos.CoreRoot"),
                        "unexpected failure: " + expected.getMessage());
            }
        }
        System.out.println("PLAT054_INVALID_OVERRIDE: PASS");
    }

    private static void applicationModules() {
        String worker =
                """
                greeting: "hello"
                start: (initial) => {
                    count: initial
                    {
                        increment: () => {
                            count = count + 1
                            count
                        }
                    }
                }
                """;
        try (Context context = Context.newBuilder("protos").allowCreateThread(true).build()) {
            ProtosEmbeddedModules.install(context, Map.of("app:worker", worker));
            String greeting = context.eval("protos", "import(\"app:worker\").greeting").asString();
            require("hello".equals(greeting), "app:worker import returned " + greeting);
            int count =
                    context.eval(
                                    "protos",
                                    "w: Actor.spawn(\"app:worker\", \"start\", 41)\n"
                                            + "w.request(\"increment\").value()")
                            .asInt();
            require(count == 42, "app:worker Actor answered " + count);
            Value bindings = context.getBindings("protos");
            require(!bindings.hasMember("filesystem"), "the catalog granted Filesystem authority");
            require(!bindings.hasMember("network"), "the catalog granted Network authority");
        }
        System.out.println("PLAT055_APPLICATION_MODULES: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            fail(message);
        }
    }

    private static void fail(String message) {
        System.err.println("PLAT054 PROBE FAILURE: " + message);
        System.exit(1);
    }
}
