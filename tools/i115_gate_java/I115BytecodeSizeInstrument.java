/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina.
 * See LICENSE.TXT at the repository root.
 */
package com.guillermomolina.protos.gate;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.Instruction;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.Map;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

/** Separate diagnostic JVM: reads real uncached/cached Bytecode DSL instruction counts. */
@TruffleInstrument.Registration(
        id = "i115-bytecode-size", name = "I115 Gate Bytecode Size", version = "1.0")
public final class I115BytecodeSizeInstrument extends TruffleInstrument {
    @Option(name = "", help = "Observe Bytecode DSL instructions",
            category = OptionCategory.INTERNAL)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    private final Map<RootNode, String> previous = new IdentityHashMap<>();
    private PrintWriter output;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new I115BytecodeSizeInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        if (!env.getOptions().get(ENABLED)) {
            return;
        }
        output = new PrintWriter(
                new OutputStreamWriter(env.err(), StandardCharsets.UTF_8), true);
        env.getInstrumenter().attachExecutionEventListener(
                SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).build(),
                new ExecutionEventListener() {
                    @Override
                    public void onEnter(EventContext context, VirtualFrame frame) {
                        observe(context);
                    }
                    @Override
                    public void onReturnValue(EventContext context, VirtualFrame frame,
                            Object result) {
                        observe(context);
                    }
                    @Override
                    public void onReturnExceptional(EventContext context, VirtualFrame frame,
                            Throwable exception) {
                        observe(context);
                    }
                });
    }

    private synchronized void observe(EventContext context) {
        RootNode root = context.getInstrumentedNode().getRootNode();
        if (!(root instanceof BytecodeRootNode bytecodeRoot)) {
            return;
        }
        String label = root.toString();
        if (!label.startsWith("protos-root:")) {
            return;
        }
        BytecodeNode node = bytecodeRoot.getBytecodeNode();
        if (node == null) {
            return;
        }
        int count = 0;
        int next = -1;
        for (Instruction instruction : node.getInstructions()) {
            int bci = instruction.getBytecodeIndex();
            int length = instruction.getLength();
            if (length <= 0 || (count != 0 && next != bci)) {
                throw new IllegalStateException("Invalid instruction stream for " + label);
            }
            next = Math.addExact(bci, length);
            count++;
        }
        if (count == 0) {
            return;
        }
        String state = node.getTier().name();
        String encoded = Base64.getEncoder().encodeToString(
                label.getBytes(StandardCharsets.UTF_8));
        String snapshot = encoded + "\t" + state + "\t" + count;
        if (!snapshot.equals(previous.get(root))) {
            output.println("I115_BYTECODE_SIZE_V1\t" + snapshot);
            previous.put(root, snapshot);
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (output != null) {
            output.flush();
        }
    }
}
