/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class ProtosDirectionalShutdownTest {
    private static ProtosPrelude core() throws Exception{return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));}
    private static Long i(long n){return Long.valueOf(n);}
    private static ProtosBytesValue bytes(ProtosObjectValue p,int...v){var b=new ProtosBytesValue(p);for(int x:v)b.indexedAdd(i(x));return b;}
    private record F(ProtosPrelude p,ProtosActivation a,ProtosObjectValue bp,ProtosObjectValue r){}
    private static F fixture() throws Exception{var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);return new F(p,a,bp,new ProtosObjectValue(ProtosObjectValue.rootObject()));}

    @Test void capabilitySurfaceIsHonest() throws Exception {
        var f=fixture(); ProtosStandardByteIoProtocol.install(f.r,f.bp,f.a,new PlainBackend());
        assertFalse(f.r.hasLocalSlot("shutdownRead")); assertFalse(f.r.hasLocalSlot("shutdownWrite"));
        var g=fixture(); ProtosStandardByteIoProtocol.install(g.r,g.bp,g.a,new HalfBackend());
        assertTrue(g.r.hasLocalSlot("shutdownRead")); assertTrue(g.r.hasLocalSlot("shutdownWrite"));
    }

    @Test void readShutdownCutsOverPendingReadToNullAndLeavesWriteUsable() throws Exception {
        var f=fixture(); var b=new HalfBackend(); ProtosStandardByteIoProtocol.install(f.r,f.bp,f.a,b);
        var read=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"read",List.of(i(4)),f.a);
        var read2=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"read",List.of(i(2)),f.a);
        var shut=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownRead",List.of(),f.a);
        assertSame(ProtosNullValue.INSTANCE,read.resolvedValue().orElseThrow()); assertSame(ProtosNullValue.INSTANCE,read2.resolvedValue().orElseThrow());
        assertEquals(ProtosFutureValue.State.PENDING,shut.state());
        var shut2=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownRead",List.of(),f.a);
        assertNotSame(shut,shut2); assertEquals(ProtosFutureValue.State.PENDING,shut2.state()); assertEquals(1,b.readShutdownCalls.get());
        b.readShutdown.get().succeeded(); assertSame(f.r,shut.resolvedValue().orElseThrow()); assertSame(f.r,shut2.resolvedValue().orElseThrow());
        var shut3=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownRead",List.of(),f.a);
        assertNotSame(shut,shut3); assertNotSame(shut2,shut3); assertSame(f.r,shut3.resolvedValue().orElseThrow()); assertEquals(1,b.readShutdownCalls.get());
        var later=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"read",List.of(i(1)),f.a);assertSame(ProtosNullValue.INSTANCE,later.resolvedValue().orElseThrow());
        var w=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"write",List.of(bytes(f.bp,1)),f.a);b.write.get().succeeded();assertSame(f.r,w.resolvedValue().orElseThrow());
    }

    @Test void writeShutdownWaitsForPrecedingWriteRejectsLaterWriteAndUsesFreshFollowers() throws Exception {
        var f=fixture(); var b=new HalfBackend(); ProtosStandardByteIoProtocol.install(f.r,f.bp,f.a,b);
        var w=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"write",List.of(bytes(f.bp,7)),f.a);
        var w2=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"write",List.of(bytes(f.bp,9)),f.a);
        var s1=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);
        var s2=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);
        assertNotSame(s1,s2); assertNull(b.writeShutdown.get());
        var rejected=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"write",List.of(bytes(f.bp,8)),f.a);assertEquals(ProtosFutureValue.State.FAILED,rejected.state());
        b.write.get().succeeded(); assertSame(f.r,w.resolvedValue().orElseThrow());
        assertNull(b.writeShutdown.get()); assertEquals(0,b.writeShutdownCalls.get());
        assertEquals(ProtosFutureValue.State.PENDING,s1.state()); assertEquals(ProtosFutureValue.State.PENDING,s2.state());
        b.write.get().succeeded(); assertSame(f.r,w2.resolvedValue().orElseThrow());
        assertNotNull(b.writeShutdown.get()); assertEquals(1,b.writeShutdownCalls.get());
        assertEquals(ProtosFutureValue.State.PENDING,s1.state()); assertEquals(ProtosFutureValue.State.PENDING,s2.state());
        b.writeShutdown.get().succeeded();
        assertSame(f.r,s1.resolvedValue().orElseThrow()); assertSame(f.r,s2.resolvedValue().orElseThrow());
        var s3=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);
        assertNotSame(s1,s3); assertNotSame(s2,s3); assertSame(f.r,s3.resolvedValue().orElseThrow()); assertEquals(1,b.writeShutdownCalls.get());
    }

    @Test void shutdownIsCommittedSoCancellationCannotRewriteItAndFailureIsStable() throws Exception {
        var f=fixture(); var b=new HalfBackend(); ProtosStandardByteIoProtocol.install(f.r,f.bp,f.a,b);
        var s=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);assertTrue(s.cancelRequest());assertEquals(ProtosFutureValue.State.PENDING,s.state());
        var s2=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);
        assertNotSame(s,s2); assertEquals(ProtosFutureValue.State.PENDING,s2.state()); assertEquals(1,b.writeShutdownCalls.get());
        b.writeShutdown.get().failed();assertEquals(ProtosFutureValue.State.FAILED,s.state());assertEquals(ProtosFutureValue.State.FAILED,s2.state());
        assertSame(s.failedError().orElseThrow(),s2.failedError().orElseThrow());
        var again=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.r,"shutdownWrite",List.of(),f.a);assertEquals(ProtosFutureValue.State.FAILED,again.state());
        assertSame(s.failedError().orElseThrow(),again.failedError().orElseThrow()); assertEquals(1,b.writeShutdownCalls.get());
    }

    private static class PlainBackend implements ProtosByteIoFlow.Backend {
        public ProtosByteIoFlow.Cancellation read(int n,ProtosByteIoFlow.ReadCompletion c){return()->{};}
        public ProtosByteIoFlow.Cancellation write(byte[] x,ProtosByteIoFlow.WriteCompletion c){return()->{};}
    }
    private static final class HalfBackend extends PlainBackend implements ProtosByteIoFlow.ReadShutdownBackend,ProtosByteIoFlow.WriteShutdownBackend {
        final AtomicReference<ProtosByteIoFlow.ReadCompletion> read=new AtomicReference<>();
        final AtomicReference<ProtosByteIoFlow.WriteCompletion> write=new AtomicReference<>();
        final AtomicReference<ProtosByteIoFlow.ShutdownCompletion> readShutdown=new AtomicReference<>();
        final AtomicReference<ProtosByteIoFlow.ShutdownCompletion> writeShutdown=new AtomicReference<>();
        final AtomicInteger readShutdownCalls=new AtomicInteger();
        final AtomicInteger writeShutdownCalls=new AtomicInteger();
        @Override public ProtosByteIoFlow.Cancellation read(int n,ProtosByteIoFlow.ReadCompletion c){read.set(c);return()->{};}
        @Override public ProtosByteIoFlow.Cancellation write(byte[] x,ProtosByteIoFlow.WriteCompletion c){write.set(c);return()->{};}
        public ProtosByteIoFlow.Cancellation shutdownRead(ProtosByteIoFlow.ShutdownCompletion c){readShutdownCalls.incrementAndGet();readShutdown.set(c);return()->{};}
        public ProtosByteIoFlow.Cancellation shutdownWrite(ProtosByteIoFlow.ShutdownCompletion c){writeShutdownCalls.incrementAndGet();writeShutdown.set(c);return()->{};}
    }
}
