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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosArrayValue;
import com.guillermomolina.protos.runtime.ProtosBooleanValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.ProtosSignalException;
import com.guillermomolina.protos.runtime.ProtosStringValue;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ProtosCommandLineSpecModuleTest {
    private static final Path CORE = Path.of("protos", "lib", "core");
    private static final Path STANDARD_LIBRARY = Path.of("protos", "lib");

    @Test
    void importedModuleExportsExactlyA1SpecificationConstructors() throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);

        Object imported =
                new ProtosSourceCompiler()
                        .compile("import(\"std:cli/CommandLine\")")
                        .call(prelude.newModuleActivation());
        ProtosObjectValue module = assertInstanceOf(ProtosObjectValue.class, imported);

        assertEquals(Set.of("option", "positional", "command"), module.localSlotsSnapshot().keySet());
    }

    @Test
    void optionAndPositionalConstructorsReturnFreshCanonicalFrozenRecords() throws Exception {
        Object result =
                evaluate(
                        """
                        CommandLine: import("std:cli/CommandLine")

                        optionDescriptor: {
                            key: "jobs"
                            long: "jobs"
                            short: "j"
                            minValues: 1
                            maxValues: 1
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: "COUNT"
                            help: "Number of workers"
                        }
                        positionalDescriptor: {
                            key: "files"
                            minOccurrences: 1
                            maxOccurrences: null
                            valueName: "FILE"
                            help: "Input files"
                        }

                        first: CommandLine.option(optionDescriptor)
                        second: CommandLine.option(optionDescriptor)
                        positional: CommandLine.positional(positionalDescriptor)

                        (optionDescriptor.hasSlot("kind") === false) &&
                            (positionalDescriptor.hasSlot("kind") === false) &&
                            (first !== second) &&
                            (first.kind == "option") &&
                            (first.key == "jobs") &&
                            (first.long == "jobs") &&
                            (first.short == "j") &&
                            (first.minValues == 1) &&
                            (first.maxValues == 1) &&
                            (first.minOccurrences == 0) &&
                            (first.maxOccurrences == 1) &&
                            (first.valueName == "COUNT") &&
                            (first.help == "Number of workers") &&
                            (positional.kind == "positional") &&
                            (positional.key == "files") &&
                            (positional.minOccurrences == 1) &&
                            (positional.maxOccurrences === null) &&
                            (positional.valueName == "FILE") &&
                            (positional.help == "Input files")
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);

        ProtosObjectValue option =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        evaluate(
                                """
                                CommandLine: import("std:cli/CommandLine")
                                CommandLine.option({
                                    key: "verbose"
                                    long: "verbose"
                                    short: "v"
                                    minValues: 0
                                    maxValues: 0
                                    minOccurrences: 0
                                    maxOccurrences: 1
                                    valueName: null
                                    help: null
                                })
                                """));
        assertTrue(option.isFrozen());
        assertEquals("option", stringSlot(option, "kind"));
        assertEquals(
                Set.of(
                        "kind",
                        "key",
                        "long",
                        "short",
                        "minValues",
                        "maxValues",
                        "minOccurrences",
                        "maxOccurrences",
                        "valueName",
                        "help"),
                option.localSlotsSnapshot().keySet());

        ProtosObjectValue positional =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        evaluate(
                                """
                                CommandLine: import("std:cli/CommandLine")
                                CommandLine.positional({
                                    key: "target"
                                    minOccurrences: 0
                                    maxOccurrences: 1
                                    valueName: null
                                    help: null
                                })
                                """));
        assertTrue(positional.isFrozen());
        assertEquals("positional", stringSlot(positional, "kind"));
        assertEquals(
                Set.of("kind", "key", "minOccurrences", "maxOccurrences", "valueName", "help"),
                positional.localSlotsSnapshot().keySet());
    }

    @Test
    void commandCanonicalizesSnapshotsAndFreezesCompleteNestedGraph() throws Exception {
        Object result =
                evaluate(
                        """
                        CommandLine: import("std:cli/CommandLine")

                        jobs: {
                            key: "jobs"
                            long: "jobs"
                            short: "j"
                            minValues: 1
                            maxValues: 1
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: "COUNT"
                            help: "Workers"
                        }
                        file: {
                            key: "file"
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: "FILE"
                            help: null
                        }
                        childDescriptor: {
                            name: "run"
                            help: "Run a target"
                            options: Array()
                            positionals: Array()
                            subcommands: Array()
                        }
                        optionsInput: Array(jobs)
                        positionalsInput: Array(file)
                        subcommandsInput: Array(childDescriptor)
                        rootDescriptor: {
                            name: "tool"
                            help: "Tool root"
                            options: optionsInput
                            positionals: positionalsInput
                            subcommands: subcommandsInput
                        }

                        first: CommandLine.command(rootDescriptor)
                        second: CommandLine.command(rootDescriptor)

                        optionsInput[0] = {
                            key: "other"
                            long: "other"
                            short: "o"
                            minValues: 0
                            maxValues: 0
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        }
                        positionalsInput[0] = {
                            key: "other"
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        }
                        subcommandsInput[0] = {
                            name: "other"
                            help: null
                            options: Array()
                            positionals: Array()
                            subcommands: Array()
                        }

                        (first !== second) &&
                            (first.options !== optionsInput) &&
                            (first.positionals !== positionalsInput) &&
                            (first.subcommands !== subcommandsInput) &&
                            (first.options[0] !== jobs) &&
                            (first.positionals[0] !== file) &&
                            (first.subcommands[0] !== childDescriptor) &&
                            (first.options !== second.options) &&
                            (first.positionals !== second.positionals) &&
                            (first.subcommands !== second.subcommands) &&
                            (first.kind == "command") &&
                            (first.name == "tool") &&
                            (first.options.size() == 1) &&
                            (first.options[0].key == "jobs") &&
                            (first.positionals.size() == 1) &&
                            (first.positionals[0].key == "file") &&
                            (first.subcommands.size() == 1) &&
                            (first.subcommands[0].name == "run") &&
                            (first.subcommands[0].options.size() == 0)
                        """);

        assertSame(ProtosBooleanValue.TRUE, result);

        ProtosObjectValue command =
                assertInstanceOf(
                        ProtosObjectValue.class,
                        evaluate(
                                """
                                CommandLine: import("std:cli/CommandLine")
                                CommandLine.command({
                                    name: "tool"
                                    help: null
                                    options: Array({
                                        key: "verbose"
                                        long: "verbose"
                                        short: "v"
                                        minValues: 0
                                        maxValues: 0
                                        minOccurrences: 0
                                        maxOccurrences: 1
                                        valueName: null
                                        help: null
                                    })
                                    positionals: Array({
                                        key: "files"
                                        minOccurrences: 0
                                        maxOccurrences: null
                                        valueName: "FILE"
                                        help: null
                                    })
                                    subcommands: Array({
                                        name: "run"
                                        help: null
                                        options: Array()
                                        positionals: Array()
                                        subcommands: Array()
                                    })
                                })
                                """));

        assertTrue(command.isFrozen());
        assertEquals("command", stringSlot(command, "kind"));
        assertEquals("tool", stringSlot(command, "name"));
        assertEquals(
                Set.of("kind", "name", "help", "options", "positionals", "subcommands"),
                command.localSlotsSnapshot().keySet());

        ProtosArrayValue options = arraySlot(command, "options");
        ProtosArrayValue positionals = arraySlot(command, "positionals");
        ProtosArrayValue subcommands = arraySlot(command, "subcommands");
        assertTrue(options.isFrozen());
        assertTrue(positionals.isFrozen());
        assertTrue(subcommands.isFrozen());

        ProtosObjectValue option =
                assertInstanceOf(ProtosObjectValue.class, options.indexedAt(BigInteger.ZERO));
        ProtosObjectValue positional =
                assertInstanceOf(ProtosObjectValue.class, positionals.indexedAt(BigInteger.ZERO));
        ProtosObjectValue child =
                assertInstanceOf(ProtosObjectValue.class, subcommands.indexedAt(BigInteger.ZERO));
        assertTrue(option.isFrozen());
        assertTrue(positional.isFrozen());
        assertTrue(child.isFrozen());
        assertTrue(arraySlot(child, "options").isFrozen());
        assertTrue(arraySlot(child, "positionals").isFrozen());
        assertTrue(arraySlot(child, "subcommands").isFrozen());
    }

    @Test
    void a1RejectsMalformedNamesCardinalitiesAndDuplicateCommandMembers() throws Exception {
        assertSignals(
                """
                CommandLine.option({
                    key: ""
                    long: "jobs"
                    short: "j"
                    minValues: 1
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 1
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.option({
                    key: "jobs"
                    long: "1jobs"
                    short: "j"
                    minValues: 1
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 1
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.option({
                    key: "jobs"
                    long: "jobs"
                    short: "jj"
                    minValues: 1
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 1
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.option({
                    key: "jobs"
                    long: null
                    short: null
                    minValues: 1
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 1
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.option({
                    key: "jobs"
                    long: "jobs"
                    short: "j"
                    minValues: 0
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 1
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.option({
                    key: "jobs"
                    long: "jobs"
                    short: "j"
                    minValues: 1
                    maxValues: 1
                    minOccurrences: 0
                    maxOccurrences: 2
                    valueName: null
                    help: null
                })
                """);
        assertSignals(
                """
                CommandLine.command({
                    name: "tool"
                    help: null
                    options: Array(
                        {
                            key: "same"
                            long: "first"
                            short: null
                            minValues: 0
                            maxValues: 0
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        },
                        {
                            key: "same"
                            long: "second"
                            short: null
                            minValues: 0
                            maxValues: 0
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        }
                    )
                    positionals: Array()
                    subcommands: Array()
                })
                """);
        assertSignals(
                """
                CommandLine.command({
                    name: "tool"
                    help: null
                    options: Array(
                        {
                            key: "a"
                            long: "same"
                            short: null
                            minValues: 0
                            maxValues: 0
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        },
                        {
                            key: "b"
                            long: "same"
                            short: null
                            minValues: 0
                            maxValues: 0
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        }
                    )
                    positionals: Array()
                    subcommands: Array()
                })
                """);
        assertSignals(
                """
                CommandLine.command({
                    name: "tool"
                    help: null
                    options: Array()
                    positionals: Array(
                        {
                            key: "rest"
                            minOccurrences: 0
                            maxOccurrences: null
                            valueName: null
                            help: null
                        },
                        {
                            key: "tail"
                            minOccurrences: 0
                            maxOccurrences: 1
                            valueName: null
                            help: null
                        }
                    )
                    subcommands: Array()
                })
                """);
        assertSignals(
                """
                CommandLine.command({
                    name: "tool"
                    help: null
                    options: Array()
                    positionals: Array()
                    subcommands: Array(
                        {
                            name: "run"
                            help: null
                            options: Array()
                            positionals: Array()
                            subcommands: Array()
                        },
                        {
                            name: "run"
                            help: null
                            options: Array()
                            positionals: Array()
                            subcommands: Array()
                        }
                    )
                })
                """);
    }

    @Test
    void frozenCanonicalAggregatesRejectLaterMutation() throws Exception {
        assertSignals(
                """
                spec: CommandLine.command({
                    name: "tool"
                    help: null
                    options: Array({
                        key: "verbose"
                        long: "verbose"
                        short: "v"
                        minValues: 0
                        maxValues: 0
                        minOccurrences: 0
                        maxOccurrences: 1
                        valueName: null
                        help: null
                    })
                    positionals: Array()
                    subcommands: Array()
                })
                spec.options[0] = spec.options[0]
                """);
    }

    private static void assertSignals(String body) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        String source = "CommandLine: import(\"std:cli/CommandLine\")\n" + body;
        assertThrows(
                ProtosSignalException.class,
                () -> new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation()),
                body);
    }

    private static Object evaluate(String source) throws Exception {
        ProtosStandardLibraryModuleResolver resolver =
                new ProtosStandardLibraryModuleResolver(STANDARD_LIBRARY);
        ProtosPrelude prelude = new ProtosCoreBootstrap().bootstrap(CORE, resolver);
        return new ProtosSourceCompiler().compile(source).call(prelude.newModuleActivation());
    }

    private static String stringSlot(ProtosObjectValue object, String name) {
        return assertInstanceOf(ProtosStringValue.class, object.localSlotsSnapshot().get(name)).value();
    }

    private static ProtosArrayValue arraySlot(ProtosObjectValue object, String name) {
        return assertInstanceOf(
                ProtosArrayValue.class, object.localSlotsSnapshot().get(name), name);
    }
}
