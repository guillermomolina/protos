/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProtosBufferedByteIoPlat031FoundationTest {
    @Test
    void bufferedWrapperOwnsOneCommonLifecycleAndReqRetainsOnlyOperationReference() throws Exception {
        Field lifecycle = ProtosBufferedByteIo.class.getDeclaredField("lifecycle");
        assertEquals(ProtosIoLifecycle.class, lifecycle.getType());

        Class<?> req = Class.forName("com.guillermomolina.protos.runtime.ProtosBufferedByteIo$Req");
        assertEquals(ProtosIoOperation.class, req.getDeclaredField("operation").getType());

        Set<String> fields =
                Arrays.stream(req.getDeclaredFields())
                        .map(Field::getName)
                        .collect(Collectors.toSet());

        assertFalse(fields.contains("f"), "Req must not own a second result Future");
        assertFalse(fields.contains("committed"), "Req must not own semantic commitment");
        assertFalse(fields.contains("cancelRequested"), "Req must not own cancellation state");
        assertFalse(fields.contains("closeCutover"), "Req must not own lifecycle cutover");

        assertTrue(fields.contains("lower"), "lower Future leaf bookkeeping remains adapter metadata");
        assertTrue(fields.contains("kind"));
        assertTrue(fields.contains("maximum"));
        assertTrue(fields.contains("bytes"));
    }
}
