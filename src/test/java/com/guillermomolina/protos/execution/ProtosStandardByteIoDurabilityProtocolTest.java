/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProtosStandardByteIoDurabilityProtocolTest {
    private static ProtosPrelude core() throws Exception{return new ProtosCoreBootstrap().bootstrap(Path.of("protos","lib","core"));}
    private static ProtosIntegerValue i(long n){return new ProtosIntegerValue(BigInteger.valueOf(n));}
    private static ProtosBytesValue bytes(ProtosObjectValue p,int...v){var b=new ProtosBytesValue(p);for(int x:v)b.indexedAdd(i(x));return b;}
    private static Fixture fixture(boolean syncable)throws Exception{
        var p=core();var a=p.newModuleActivation();var bp=new ProtosObjectValue(ProtosObjectValue.rootObject());ProtosStandardBytesProtocol.install(bp);
        var r=new ProtosObjectValue(ProtosObjectValue.rootObject());var b=syncable?new SyncMemoryBackend():new ExtendedMemoryBackend();
        ProtosStandardByteIoProtocol.installExtended(r,bp,a,b);return new Fixture(a,bp,r,b);
    }

    @Test void syncSurfaceIsExposedOnlyForSyncableBackendAndUsesFutureContract()throws Exception{
        var plain=fixture(false);assertFalse(plain.receiver.hasLocalSlot("sync"));
        var f=fixture(true);assertTrue(f.receiver.hasLocalSlot("sync"));
        var sync=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertSame(f.receiver,sync.resolvedValue().orElseThrow());
        var bad=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(i(1)),f.activation);
        assertEquals(ProtosFutureValue.State.FAILED,bad.state());
    }

    @Test void writeFlushTruncateAndSyncShareOneOrderingDomain()throws Exception{
        var f=fixture(true);var b=(SyncMemoryBackend)f.backend;b.holdWrite=true;
        var write=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,1,2,3)),f.activation);
        var flush=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"flush",List.of(),f.activation);
        var truncate=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"truncate",List.of(i(2)),f.activation);
        var sync=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertEquals(ProtosFutureValue.State.PENDING,write.state());assertEquals(0,b.syncCalls);
        b.completeHeldWrite();
        assertSame(f.receiver,flush.resolvedValue().orElseThrow());assertSame(f.receiver,truncate.resolvedValue().orElseThrow());
        assertSame(f.receiver,sync.resolvedValue().orElseThrow());assertArrayEquals(new byte[]{1,2},b.durable);
    }

    @Test void laterWriteIsOutsideEarlierSyncFrontier()throws Exception{
        var f=fixture(true);var b=(SyncMemoryBackend)f.backend;b.holdSync=true;
        ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,1)),f.activation);
        var sync=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        var later=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,2)),f.activation);
        assertEquals(ProtosFutureValue.State.PENDING,sync.state());assertEquals(ProtosFutureValue.State.PENDING,later.state());
        assertArrayEquals(new byte[]{1},b.durable);b.completeHeldSyncSuccess();
        assertSame(f.receiver,sync.resolvedValue().orElseThrow());assertSame(f.receiver,later.resolvedValue().orElseThrow());
        assertArrayEquals(new byte[]{1},b.durable);
    }

    @Test void cancellationBeforeCommitWinsWithoutDurability()throws Exception{
        var f=fixture(true);var b=(SyncMemoryBackend)f.backend;b.holdBeforeCommit=true;
        ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,7)),f.activation);
        var sync=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertTrue(sync.cancelRequest());assertEquals(ProtosFutureValue.State.CANCELLED,sync.state());assertTrue(b.syncCancelRequested);
        assertFalse(b.tryCommitHeldSync());assertEquals(0,b.durable.length);
    }

    @Test void cancellationAfterCommitCannotRewriteSyncOutcome()throws Exception{
        var f=fixture(true);var b=(SyncMemoryBackend)f.backend;b.holdSync=true;
        ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,9)),f.activation);
        var sync=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertArrayEquals(new byte[]{9},b.durable);assertTrue(sync.cancelRequest());assertEquals(ProtosFutureValue.State.PENDING,sync.state());
        b.completeHeldSyncSuccess();assertSame(f.receiver,sync.resolvedValue().orElseThrow());
    }

    @Test void failedCommittedSyncDoesNotRollbackAndLaterSyncCanSucceed()throws Exception{
        var f=fixture(true);var b=(SyncMemoryBackend)f.backend;b.failSync=true;
        ProtosInvocation.invokeMessage(f.receiver,"write",List.of(bytes(f.bytesPrototype,4,5)),f.activation);
        var first=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertEquals(ProtosFutureValue.State.FAILED,first.state());assertArrayEquals(new byte[]{4,5},b.durable);
        b.failSync=false;var second=(ProtosFutureValue)ProtosInvocation.invokeMessage(f.receiver,"sync",List.of(),f.activation);
        assertSame(f.receiver,second.resolvedValue().orElseThrow());assertArrayEquals(new byte[]{4,5},b.durable);
    }

    private record Fixture(ProtosActivation activation,ProtosObjectValue bytesPrototype,ProtosObjectValue receiver,ExtendedMemoryBackend backend){}

    private static class ExtendedMemoryBackend implements ProtosByteIoFlow.ExtendedBackend{
        byte[]data=new byte[0];int position;boolean holdWrite;byte[]heldWrite;ProtosByteIoFlow.WriteCompletion heldWriteCompletion;
        public ProtosByteIoFlow.Cancellation read(int max,ProtosByteIoFlow.ReadCompletion c){if(position>=data.length)c.eof();else{int n=Math.min(max,data.length-position);var x=Arrays.copyOfRange(data,position,position+n);position+=n;c.data(x);}return()->{};}
        public ProtosByteIoFlow.Cancellation write(byte[]x,ProtosByteIoFlow.WriteCompletion c){if(holdWrite){heldWrite=x.clone();heldWriteCompletion=c;return()->{};}writeNow(x);c.succeeded();return()->{};}
        void completeHeldWrite(){var x=heldWrite;var c=heldWriteCompletion;heldWrite=null;heldWriteCompletion=null;holdWrite=false;writeNow(x);c.succeeded();}
        void writeNow(byte[]x){int end=position+x.length;if(end>data.length)data=Arrays.copyOf(data,end);System.arraycopy(x,0,data,position,x.length);position=end;}
        public ProtosByteIoFlow.Cancellation flush(ProtosByteIoFlow.ReceiverCompletion c){c.succeeded();return()->{};}
        public ProtosByteIoFlow.Cancellation position(ProtosByteIoFlow.IntegerCompletion c){c.succeeded(BigInteger.valueOf(position));return()->{};}
        public ProtosByteIoFlow.Cancellation seek(BigInteger p,ProtosByteIoFlow.IntegerCompletion c){position=p.intValueExact();c.succeeded(p);return()->{};}
        public ProtosByteIoFlow.Cancellation seekBy(BigInteger o,ProtosByteIoFlow.IntegerCompletion c){var p=BigInteger.valueOf(position).add(o);if(p.signum()<0)c.failed();else{position=p.intValueExact();c.succeeded(p);}return()->{};}
        public ProtosByteIoFlow.Cancellation seekToEnd(ProtosByteIoFlow.IntegerCompletion c){position=data.length;c.succeeded(BigInteger.valueOf(position));return()->{};}
        public ProtosByteIoFlow.Cancellation size(ProtosByteIoFlow.IntegerCompletion c){c.succeeded(BigInteger.valueOf(data.length));return()->{};}
        public ProtosByteIoFlow.Cancellation truncate(BigInteger n,ProtosByteIoFlow.ReceiverCompletion c){int x=n.intValueExact();if(x<data.length)data=Arrays.copyOf(data,x);c.succeeded();return()->{};}
    }

    private static final class SyncMemoryBackend extends ExtendedMemoryBackend implements ProtosByteIoFlow.SyncBackend{
        int syncCalls;byte[]durable=new byte[0];boolean holdSync,holdBeforeCommit,failSync,syncCancelRequested;
        ProtosByteIoFlow.SyncCompletion heldSync;
        public ProtosByteIoFlow.Cancellation sync(ProtosByteIoFlow.SyncCompletion c){
            syncCalls++;heldSync=c;
            if(holdBeforeCommit)return()->syncCancelRequested=true;
            if(!c.commit())return()->syncCancelRequested=true;
            durable=data.clone();
            if(holdSync)return()->syncCancelRequested=true;
            if(failSync)c.failed();else c.succeeded();
            return()->syncCancelRequested=true;
        }
        boolean tryCommitHeldSync(){if(heldSync==null)return false;boolean ok=heldSync.commit();if(ok)durable=data.clone();return ok;}
        void completeHeldSyncSuccess(){var c=heldSync;heldSync=null;holdSync=false;c.succeeded();}
    }
}
