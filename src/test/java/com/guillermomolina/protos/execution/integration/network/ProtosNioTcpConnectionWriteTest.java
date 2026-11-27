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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.guillermomolina.protos.runtime.io.ProtosTcpConnectionValue;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProtosNioTcpConnectionWriteTest {
    private static final Path CORE=Path.of("protos","lib","core");
    private static final Duration WAIT=Duration.ofSeconds(10);

    @Test
    void connectedWriteDeliversCapturedBytes() throws Exception {
        try (Fixture x=connected("protos-test-nio-write-immediate")) {
            byte[] payload={1,2,3,4,5};
            ProtosFutureValue write=write(x,payload);
            assertArrayEquals(payload,readExactly(x.peer,payload.length));
            awaitTerminal(write);
            assertEquals(ProtosFutureValue.State.RESOLVED,write.state());
            assertSame(x.connection,write.resolvedValue().orElseThrow());
        }
    }

    @Test
    void largeWriteRetainsOrderedProgressAcrossPostFirstEffectCancellation() throws Exception {
        try (Fixture x=connected("protos-test-nio-write-partial")) {
            byte[] payload=payload(1024*1024);
            ProtosFutureValue write=write(x,payload);
            byte[] received=new byte[payload.length];
            readIntoExactly(x.peer,received,0,1);
            write.cancelRequest();
            assertNotEquals(ProtosFutureValue.State.CANCELLED,write.state());
            readIntoExactly(x.peer,received,1,received.length-1);
            awaitTerminal(write);
            assertEquals(ProtosFutureValue.State.RESOLVED,write.state());
            assertArrayEquals(payload,received);
        }
    }

    @Test
    void readAndWriteProgressRemainIndependent() throws Exception {
        try (Fixture x=connected("protos-test-nio-write-duplex")) {
            ProtosFutureValue read=(ProtosFutureValue)ProtosInvocation.invokeMessage(
                    x.connection,"read",List.of(Long.valueOf(4)),x.activation);
            assertEquals(ProtosFutureValue.State.PENDING,read.state());
            byte[] outgoing=payload(1024*1024);
            ProtosFutureValue write=write(x,outgoing);
            writeFully(x.peer,new byte[]{7,8,9,10});
            awaitTerminal(read);
            assertEquals(List.of(7,8,9,10),resolvedBytes(read));
            byte[] received=new byte[outgoing.length];
            readIntoExactly(x.peer,received,0,received.length);
            awaitTerminal(write);
            assertEquals(ProtosFutureValue.State.RESOLVED,write.state());
            assertArrayEquals(outgoing,received);
        }
    }

    @Test void abortStopsACommittedPrefixWithoutAnotherWriteAttempt() throws Exception { controlledAbort(1,0,false); }
    @Test void abortRejectsContinuationEvenForCommittedOutput() throws Exception { controlledAbort(1,1,false); }
    @Test void abortDuringFirstAttemptWithZeroEffect() throws Exception { controlledAbort(0,0,false); }
    @Test void abortDuringFirstAttemptWithPositiveIncompleteEffect() throws Exception { controlledAbort(0,1,false); }
    @Test void finalAdmittedAttemptPreservesCompleteSuccessAfterAbort() throws Exception { controlledAbort(1,2,false); }
    @Test void admittedAttemptIndependentFailureSurvivesAbort() throws Exception { controlledAbort(1,0,true); }
    @Test void completeFirstAttemptAfterAbortIsNotDuplicated() throws Exception { controlledAbort(0,3,false); }

    private static void controlledAbort(int prefix,int finalCount,boolean fail) throws Exception {
        try(var x=new ControlledFixture(prefix,finalCount,fail)) {
            var write=x.write();
            if(prefix>0){x.barrier();x.poller.submit(()->x.backend.ready(x.key),e->{throw new AssertionError(e);});}
            org.junit.jupiter.api.Assertions.assertTrue(x.channel.entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var abort=x.call("abort");
            org.junit.jupiter.api.Assertions.assertTrue(abort.isPending());
            x.channel.proceed.countDown();x.barrier();x.publish();
            if(!fail&&prefix+finalCount==3)assertSame(x.connection,write.resolvedValue().orElseThrow());
            else {
                assertEquals(ProtosFutureValue.State.FAILED,write.state());
                String category=prefix+finalCount==0&&!fail?"IOLifecycleError":"IOError";
                assertSame(x.prelude.bindings().readLocalSlot(category).orElseThrow(),
                        write.failedError().orElseThrow().parent().orElseThrow());
            }
            assertSame(x.connection,abort.resolvedValue().orElseThrow());
            assertEquals(prefix>0?2:1,x.channel.writeCalls);
            assertEquals(prefix+(fail?0:finalCount),x.channel.output.size());
            assertEquals(1,x.channel.closeCalls);
        }
    }

    static final class ControlledFixture implements AutoCloseable {
        final ProtosNioHostIoPoller poller=new ProtosNioHostIoPoller("tcp-controlled-attempt");
        final ControlledChannel channel;
        final java.nio.channels.SelectionKey key;
        final ProtosNioTcpConnectionBackend backend;
        final ProtosPrelude prelude=new ProtosCoreBootstrap().bootstrap(CORE);
        final ProtosActivation activation=prelude.newModuleActivation();
        final ProtosTcpConnectionValue connection;
        ControlledFixture(int prefix,int finalCount,boolean failure) throws Exception {
            channel=new ControlledChannel(prefix,finalCount,failure);
            key=new java.nio.channels.SelectionKey(){
                boolean valid=true;int interests;
                public java.nio.channels.SelectableChannel channel(){return channel;}
                public java.nio.channels.Selector selector(){return null;}
                public boolean isValid(){return valid;}
                public void cancel(){valid=false;}
                public int interestOps(){return interests;}
                public java.nio.channels.SelectionKey interestOps(int ops){interests=ops;return this;}
                public int readyOps(){return java.nio.channels.SelectionKey.OP_WRITE;}
            };
            backend=new ProtosNioTcpConnectionBackend(poller,channel);backend.attachKey(key);
            connection=new ProtosTcpConnectionValue(prelude,new Object(),activation,backend);
        }
        ProtosFutureValue write(){
            return (ProtosFutureValue)ProtosInvocation.invokeMessage(connection,"write",List.of(
                    new ProtosBytesValue(prelude.bytesPrototypeForRuntime(),new byte[]{1,2,3})),activation);
        }
        ProtosFutureValue call(String selector){
            return (ProtosFutureValue)ProtosInvocation.invokeMessage(connection,selector,List.of(),activation);
        }
        void barrier() throws Exception {
            var done=new java.util.concurrent.CountDownLatch(1);
            var failure=new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
            poller.submit(done::countDown,e->{failure.set(e);done.countDown();});
            org.junit.jupiter.api.Assertions.assertTrue(done.await(5,java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertNull(failure.get());
        }
        void publish(){while(activation.executionDomain().dispatchTargetedRuntimeCompletionWithoutHandoffForRuntime()) {}}
        public void close() throws Exception {channel.proceed.countDown();channel.closeProceed.countDown();backend.releaseIfUntransferred();poller.close();channel.close();}
    }

    /** A controlled host boundary; latches choose admission/effect/release ordering, never sleeps. */
    static final class ControlledChannel extends SocketChannel {
        final int prefix;final int finalCount;final boolean failure;
        final java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch proceed=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch closeEntered=new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch closeProceed=new java.util.concurrent.CountDownLatch(1);
        final java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();
        volatile boolean holdClose;volatile boolean failClose;volatile int writeCalls;volatile int closeCalls;
        ControlledChannel(int prefix,int finalCount,boolean failure){super(java.nio.channels.spi.SelectorProvider.provider());this.prefix=prefix;this.finalCount=finalCount;this.failure=failure;}
        public int write(ByteBuffer buffer)throws IOException {
            writeCalls++;
            if(prefix>0&&writeCalls==1)return consume(buffer,prefix);
            entered.countDown();await(proceed);
            if(failure)throw new IOException("controlled write failure");
            return consume(buffer,finalCount);
        }
        private int consume(ByteBuffer b,int count){for(int i=0;i<count;i++)output.write(b.get());return count;}
        private static void await(java.util.concurrent.CountDownLatch latch)throws IOException {
            try{if(!latch.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("control barrier timed out");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
        }
        protected void implCloseSelectableChannel()throws IOException {
            closeCalls++;closeEntered.countDown();if(holdClose)await(closeProceed);
            if(failClose)throw new IOException("controlled release failure");
        }
        protected void implConfigureBlocking(boolean block){}
        public int read(ByteBuffer b){return 0;}
        public long read(ByteBuffer[] b,int offset,int length){return 0;}
        public long write(ByteBuffer[] b,int offset,int length)throws IOException{return write(b[offset]);}
        public java.net.Socket socket(){throw new UnsupportedOperationException();}
        public boolean isConnected(){return true;}
        public boolean isConnectionPending(){return false;}
        public boolean connect(java.net.SocketAddress remote){return true;}
        public boolean finishConnect(){return true;}
        public SocketChannel bind(java.net.SocketAddress local){return this;}
        public SocketChannel shutdownInput(){return this;}
        public SocketChannel shutdownOutput(){return this;}
        public java.net.SocketAddress getRemoteAddress(){return null;}
        public java.net.SocketAddress getLocalAddress(){return null;}
        public <T> SocketChannel setOption(java.net.SocketOption<T> option,T value){return this;}
        public <T> T getOption(java.net.SocketOption<T> option){return null;}
        public java.util.Set<java.net.SocketOption<?>> supportedOptions(){return java.util.Set.of();}
    }

    private static Fixture connected(String name) throws Exception {
        ProtosNioHostIoPoller poller=new ProtosNioHostIoPoller(name);
        ServerSocketChannel server=ServerSocketChannel.open(StandardProtocolFamily.INET);
        SocketChannel peer=null;
        try {
            Inet4Address loopback=(Inet4Address)InetAddress.getByAddress(new byte[]{127,0,0,1});
            server.bind(new InetSocketAddress(loopback,0)); server.configureBlocking(false);
            ProtosPrelude prelude=new ProtosCoreBootstrap().bootstrap(CORE);
            ProtosActivation activation=prelude.newModuleActivation();
            ProtosNioNetworkBackend backend=new ProtosNioNetworkBackend(
                    poller,bytes->{throw new AssertionError("IPv6 resolver used by IPv4");});
            ProtosNetworkCapabilityValue network=new ProtosNetworkCapabilityValue(prelude,backend);
            int port=((InetSocketAddress)server.getLocalAddress()).getPort();
            ProtosFutureValue connect=(ProtosFutureValue)ProtosInvocation.invokeMessage(
                    network,"connectTcp",List.of(endpoint(prelude,activation,port)),activation);
            awaitTerminal(connect);
            assertEquals(ProtosFutureValue.State.RESOLVED,connect.state());
            peer=awaitAccept(server); peer.configureBlocking(true);
            ProtosTcpConnectionValue connection=assertInstanceOf(
                    ProtosTcpConnectionValue.class,connect.resolvedValue().orElseThrow());
            return new Fixture(poller,server,peer,prelude,activation,connection);
        } catch(Throwable e) {
            if(peer!=null) try{peer.close();}catch(Exception ignored){}
            try{server.close();}catch(Exception ignored){}
            poller.close(); throw e;
        }
    }

    private static ProtosObjectValue endpoint(ProtosPrelude p,ProtosActivation a,int port) {
        Object af=p.ipAddressPrototypeForRuntime();
        ProtosObjectValue address=(ProtosObjectValue)ProtosInvocation.invoke(af,List.of(
                Long.valueOf(4),
                Long.valueOf(0x7f000001L)),a);
        Object ef=p.ipEndpointPrototypeForRuntime();
        return (ProtosObjectValue)ProtosInvocation.invoke(
                ef,List.of(address,Long.valueOf(port)),a);
    }

    private static ProtosFutureValue write(Fixture x,byte[] bytes) {
        ProtosBytesValue value=new ProtosBytesValue(x.prelude.bytesPrototypeForRuntime());
        for(byte b:bytes)value.indexedAdd(Long.valueOf(b&0xff));
        return (ProtosFutureValue)ProtosInvocation.invokeMessage(
                x.connection,"write",List.of(value),x.activation);
    }
    private static byte[] payload(int n) {
        byte[] b=new byte[n]; for(int i=0;i<n;i++)b[i]=(byte)(i*31+17); return b;
    }
    private static List<Integer> unsigned(byte[] b) {
        ArrayList<Integer> r=new ArrayList<>(b.length); for(byte v:b)r.add(v&0xff); return r;
    }
    private static List<Integer> resolvedBytes(ProtosFutureValue f) {
        ProtosBytesValue b=assertInstanceOf(ProtosBytesValue.class,f.resolvedValue().orElseThrow());
        ArrayList<Integer> r=new ArrayList<>();
        for(Object v:b.indexedSnapshot())r.add(Integer.parseInt(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(v)));
        return r;
    }
    private static SocketChannel awaitAccept(ServerSocketChannel s)throws Exception {
        long d=System.nanoTime()+WAIT.toNanos();
        while(System.nanoTime()<d){SocketChannel c=s.accept();if(c!=null)return c;Thread.sleep(5);}
        throw new AssertionError("timed out waiting for accept");
    }
    private static byte[] readExactly(SocketChannel c,int n)throws Exception {
        byte[] b=new byte[n];readIntoExactly(c,b,0,n);return b;
    }
    private static void readIntoExactly(SocketChannel c,byte[] b,int o,int n)throws Exception {
        ByteBuffer x=ByteBuffer.wrap(b,o,n);while(x.hasRemaining()){if(c.read(x)<0)throw new AssertionError("unexpected EOF");}
    }
    private static void writeFully(SocketChannel c,byte[] b)throws Exception {
        ByteBuffer x=ByteBuffer.wrap(b);while(x.hasRemaining())c.write(x);
    }
    private static void awaitTerminal(ProtosFutureValue f)throws Exception {
        long d=System.nanoTime()+WAIT.toNanos();
        while(f.state()==ProtosFutureValue.State.PENDING&&System.nanoTime()<d)
            if (!f.domain().dispatchOne()) Thread.sleep(5);
        if(f.state()==ProtosFutureValue.State.PENDING)throw new AssertionError("timed out waiting for Future");
    }

    private static final class Fixture implements AutoCloseable {
        final ProtosNioHostIoPoller poller; final ServerSocketChannel server; final SocketChannel peer;
        final ProtosPrelude prelude; final ProtosActivation activation; final ProtosTcpConnectionValue connection;
        Fixture(ProtosNioHostIoPoller p,ServerSocketChannel s,SocketChannel q,ProtosPrelude pr,
                ProtosActivation a,ProtosTcpConnectionValue c){
            poller=p;server=s;peer=q;prelude=pr;activation=a;connection=c;
        }
        public void close()throws IOException{
            try{peer.close();}finally{try{server.close();}finally{poller.close();}}
        }
    }
}
