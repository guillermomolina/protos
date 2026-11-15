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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guillermomolina.protos.runtime.ProtosNullValue;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * I115: the result classifications are directly declared Bytecode DSL
 * operations. Each typed specialization and invalid-carrier fallback is
 * exercised directly; bytecode execution is covered by the existing C-prime
 * and continuation integration tests.
 */
class ProtosI115DirectResultClassificationTest {

    private static final String BUFFERED_WRITER_MESSAGE =
            "BufferedWriter C-prime step gate produced a non-Boolean value";
    private static final String TEXT_READER_MESSAGE =
            "TextReader C-prime advance produced an invalid carrier";

    @Test
    void bufferedWriterStepGateReturnsTheBooleanCarrier() {
        assertTrue(ProtosBytecodeRootNode.IsBufferedByteWriterStepRequired.doBoolean(true));
        assertFalse(ProtosBytecodeRootNode.IsBufferedByteWriterStepRequired.doBoolean(false));
    }

    @Test
    void bufferedWriterStepGateRejectsInvalidCarriers() {
        for (Object invalid : Arrays.asList(null, "true", new Object())) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> ProtosBytecodeRootNode.IsBufferedByteWriterStepRequired
                            .doInvalid(invalid));
            assertEquals(BUFFERED_WRITER_MESSAGE, error.getMessage());
        }
    }

    @Test
    void textReaderNeedInputReadsTheAdvanceCarrier() {
        assertTrue(ProtosBytecodeRootNode.IsTextReaderNeedInput.doAdvance(
                ProtosTextReaderCPrimeExecution.Advance.needInput()));
        assertFalse(ProtosBytecodeRootNode.IsTextReaderNeedInput.doAdvance(
                ProtosTextReaderCPrimeExecution.Advance.terminal()));
    }

    @Test
    void textReaderNeedInputRejectsInvalidCarriers() {
        for (Object invalid : Arrays.asList(null, new Object())) {
            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> ProtosBytecodeRootNode.IsTextReaderNeedInput.doInvalid(invalid));
            assertEquals(TEXT_READER_MESSAGE, error.getMessage());
        }
    }

    @Test
    void continuationClassProfileAgreesWithTheGenericPredicate() {
        assertFalse(ProtosBytecodeRootNode.IsContinuation.perform(null));
        for (Object ordinary : List.of(
                Boolean.TRUE,
                Long.valueOf(1L),
                "text",
                ProtosNullValue.INSTANCE,
                ProtosTextReaderCPrimeExecution.Advance.terminal(),
                new Object())) {
            assertFalse(ProtosBytecodeRootNode.IsContinuation.perform(ordinary));
            assertEquals(
                    ProtosBytecodeRootNode.IsContinuation.perform(ordinary),
                    ProtosBytecodeRootNode.IsContinuation.continuationClassProfiled(
                            ordinary,
                            ordinary.getClass(),
                            ProtosBytecodeRootNode.IsContinuation.isContinuationClass(
                                    ordinary.getClass())));
        }
    }
}
