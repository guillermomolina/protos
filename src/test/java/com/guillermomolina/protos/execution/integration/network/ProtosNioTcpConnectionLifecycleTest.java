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
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosBytesValue;
import com.guillermomolina.protos.runtime.ProtosFutureValue;
import com.guillermomolina.protos.runtime.ProtosNetworkCapabilityValue;
import com.guillermomolina.protos.runtime.ProtosNullValue;
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

final class ProtosNioTcpConnectionLifecycleTest {
    private static final Path CORE=Path.of("protos","lib","core");
    private static final Duration WAIT=Duration.ofSeconds(10);

    @Test
    void shutdownWriteEndsOnlyOutputAfterEarlierBytes() throws Exception {
        try (Fixture x=connected("protos-test-nio-shutdown-write")) {
            byte[] payload=payload(256*1024);
            ProtosFutureValue write=write(x,payload);
            ProtosFutureValue shutdown=future(x,"shutdownWrite",List.of());

            assertFailedAs(write(x,new byte[]{99}),x.prelude,"IOLifecycleError");
            assertArrayEquals(payload,readExactly(x.peer,payload.length));
            awaitTerminal(write);
            awaitTerminal(shutdown);
            assertEquals(ProtosFutureValue.State.RESOLVED,write.state());
            assertEquals(ProtosFutureValue.State.RESOLVED,shutdown.state());
            assertSame(x.connection,shutdown.resolvedValue().orElseThrow());
            awaitPeerEof(x.peer);

            writeFully(x.peer,new byte[]{7,8,9,10});
            ProtosFutureValue read=future(x,"read",List.of(integer(4)));
            awaitTerminal(read);
            assertEquals(List.of(7,8,9,10),resolvedBytes(read));
        }
    }

    @Test
    void shutdownReadCutsPendingReadToLocalEofAndKeepsOutputOpen() throws Exception {
        try (Fixture x=connected("protos-test-nio-shutdown-read")) {
            ProtosFutureValue pending=future(x,"read",List.of(integer(4)));
            assertEquals(ProtosFutureValue.State.PENDING,pending.state());
            ProtosFutureValue shutdown=future(x,"shutdownRead",List.of());

            awaitTerminal(pending);
            awaitTerminal(shutdown);
            assertSame(ProtosNullValue.INSTANCE,pending.resolvedValue().orElseThrow());
            assertSame(x.connection,shutdown.resolvedValue().orElseThrow());
            ProtosFutureValue later=future(x,"read",List.of(integer(1)));
            assertSame(ProtosNullValue.INSTANCE,later.resolvedValue().orElseThrow());

            byte[] outbound={21,22,23,24};
            ProtosFutureValue write=write(x,outbound);
            assertArrayEquals(outbound,readExactly(x.peer,outbound.length));
            awaitTerminal(write);
            assertSame(x.connection,write.resolvedValue().orElseThrow());
        }
    }

    @Test
    void wholeCloseDominatesPriorReadShutdownAndClosesRemainingDirection() throws Exception {
        try (Fixture x=connected("protos-test-nio-close-after-shutdown")) {
            ProtosFutureValue shutdown=future(x,"shutdownRead",List.of());
            awaitTerminal(shutdown);
            assertEquals(ProtosFutureValue.State.RESOLVED,shutdown.state());

            ProtosFutureValue close=future(x,"close",List.of());
            awaitTerminal(close);
            assertSame(x.connection,close.resolvedValue().orElseThrow());
            assertFailedAs(future(x,"read",List.of(integer(1))),x.prelude,"IOLifecycleError");
            assertFailedAs(write(x,new byte[]{1}),x.prelude,"IOLifecycleError");
            awaitPeerEof(x.peer);
        }
    }

    @Test void physicalCloseInitiationAndRepeatedRequestsDoNotPublishEarlySuccess() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            x.channel.holdClose=true;
            var abort=x.call("abort");
            org.junit.jupiter.api.Assertions.assertTrue(x.channel.closeEntered.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var close=x.call("close");var repeated=x.call("abort");
            x.publish();org.junit.jupiter.api.Assertions.assertTrue(abort.isPending());
            org.junit.jupiter.api.Assertions.assertTrue(close.isPending());org.junit.jupiter.api.Assertions.assertTrue(repeated.isPending());
            x.channel.closeProceed.countDown();x.barrier();x.publish();
            assertSame(x.connection,abort.resolvedValue().orElseThrow());assertSame(x.connection,close.resolvedValue().orElseThrow());
            assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test void physicalReleaseFailureIsRetainedWithoutRetry() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(0,0,false)) {
            x.channel.failClose=true;var abort=x.call("abort");x.barrier();x.publish();
            assertFailedAs(abort,x.prelude,"IOError");
            assertSame(abort.failedError().orElseThrow(),x.call("close").failedError().orElseThrow());
            assertSame(abort.failedError().orElseThrow(),x.call("abort").failedError().orElseThrow());
            assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test void abortRetiresPendingDirectionalShutdowns() throws Exception {
        try(var x=new ProtosNioTcpConnectionWriteTest.ControlledFixture(1,0,false)) {
            var write=x.write();x.barrier();
            var shutdown=x.call("shutdownWrite");var abort=x.call("abort");x.barrier();x.publish();
            assertFailedAs(write,x.prelude,"IOError");assertFailedAs(shutdown,x.prelude,"IOError");
            assertSame(x.connection,abort.resolvedValue().orElseThrow());assertEquals(1,x.channel.closeCalls);
        }
    }

    @Test void abortEscalationDoesNotNeedThePeerToResumeReading() throws Exception {
        try(Fixture x=connected("protos-test-nio-abort-nonreading")) {
            // The one-byte read proves commitment, then the peer stops consuming output.
            var write=write(x,payload(1024*1024));
            readExactly(x.peer,1);
            var close=future(x,"close",List.of());
            var abort=future(x,"abort",List.of());
            awaitTerminal(abort);awaitTerminal(close);awaitTerminal(write);
            assertSame(x.connection,abort.resolvedValue().orElseThrow());
            assertSame(x.connection,close.resolvedValue().orElseThrow());
            // Kernel capacity can permit full output before cutover; the controlled tests
            // separately require incomplete committed output and forbid continuation.
            org.junit.jupiter.api.Assertions.assertNotEquals(ProtosFutureValue.State.CANCELLED,write.state());
        }
    }

    private static Fixture connected(String name) throws Exception {
        ProtosNioHostIoPoller poller=new ProtosNioHostIoPoller(name);
        ServerSocketChannel server=ServerSocketChannel.open(StandardProtocolFamily.INET);
        SocketChannel peer=null;
        try {
            Inet4Address loopback=(Inet4Address)InetAddress.getByAddress(new byte[]{127,0,0,1});
            server.bind(new InetSocketAddress(loopback,0));
            server.configureBlocking(false);
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
            peer=awaitAccept(server);
            peer.configureBlocking(false);
            ProtosTcpConnectionValue connection=assertInstanceOf(
                    ProtosTcpConnectionValue.class,connect.resolvedValue().orElseThrow());
            return new Fixture(poller,server,peer,prelude,activation,connection);
        } catch(Throwable e) {
            if(peer!=null) try{peer.close();}catch(Exception ignored){}
            try{server.close();}catch(Exception ignored){}
            poller.close();
            throw e;
        }
    }

    private static ProtosObjectValue endpoint(ProtosPrelude p,ProtosActivation a,int port) {
        Object af=p.ipAddressPrototypeForRuntime();
        ProtosObjectValue address=(ProtosObjectValue)ProtosInvocation.invoke(af,List.of(
                integer(4),Long.valueOf(0x7f000001L)),a);
        Object ef=p.ipEndpointPrototypeForRuntime();
        return (ProtosObjectValue)ProtosInvocation.invoke(
                ef,List.of(address,integer(port)),a);
    }

    private static ProtosFutureValue future(Fixture x,String selector,List<?> args) {
        return (ProtosFutureValue)ProtosInvocation.invokeMessage(x.connection,selector,args,x.activation);
    }

    private static ProtosFutureValue write(Fixture x,byte[] bytes) {
        ProtosBytesValue value=new ProtosBytesValue(x.prelude.bytesPrototypeForRuntime());
        for(byte b:bytes)value.indexedAdd(integer(b&0xff));
        return future(x,"write",List.of(value));
    }

    private static Long integer(long value) {
        return Long.valueOf(value);
    }

    private static byte[] payload(int n) {
        byte[] b=new byte[n];
        for(int i=0;i<n;i++)b[i]=(byte)(i*17+29);
        return b;
    }

    private static List<Integer> resolvedBytes(ProtosFutureValue f) {
        ProtosBytesValue b=assertInstanceOf(ProtosBytesValue.class,f.resolvedValue().orElseThrow());
        ArrayList<Integer> r=new ArrayList<>();
        for(Object v:b.indexedSnapshot())
            r.add(Integer.parseInt(com.guillermomolina.protos.runtime.ProtosNumericValueSupport.integerDecimalText(v)));
        return r;
    }

    private static void assertFailedAs(ProtosFutureValue f,ProtosPrelude p,String prototypeName) {
        assertEquals(ProtosFutureValue.State.FAILED,f.state());
        assertSame(p.bindings().readLocalSlot(prototypeName).orElseThrow(),
                f.failedError().orElseThrow().parent().orElseThrow());
    }

    private static SocketChannel awaitAccept(ServerSocketChannel s)throws Exception {
        long d=deadline();
        while(System.nanoTime()<d){
            SocketChannel c=s.accept();
            if(c!=null)return c;
            Thread.sleep(5);
        }
        throw new AssertionError("timed out waiting for accept");
    }

    private static byte[] readExactly(SocketChannel c,int n)throws Exception {
        byte[] b=new byte[n];
        ByteBuffer x=ByteBuffer.wrap(b);
        long d=deadline();
        while(x.hasRemaining()&&System.nanoTime()<d){
            int count=c.read(x);
            if(count<0)throw new AssertionError("unexpected EOF");
            if(count==0)Thread.sleep(5);
        }
        if(x.hasRemaining())throw new AssertionError("timed out waiting for peer bytes");
        return b;
    }

    private static void writeFully(SocketChannel c,byte[] b)throws Exception {
        ByteBuffer x=ByteBuffer.wrap(b);
        long d=deadline();
        while(x.hasRemaining()&&System.nanoTime()<d){
            int count=c.write(x);
            if(count==0)Thread.sleep(5);
        }
        if(x.hasRemaining())throw new AssertionError("timed out writing peer bytes");
    }

    private static void awaitPeerEof(SocketChannel c)throws Exception {
        ByteBuffer one=ByteBuffer.allocate(1);
        long d=deadline();
        while(System.nanoTime()<d){
            int count=c.read(one);
            if(count<0)return;
            if(count>0)throw new AssertionError("unexpected byte after expected output frontier");
            Thread.sleep(5);
        }
        throw new AssertionError("timed out waiting for peer EOF");
    }

    private static void awaitTerminal(ProtosFutureValue f)throws Exception {
        long d=deadline();
        while(f.state()==ProtosFutureValue.State.PENDING&&System.nanoTime()<d)
            if (!f.domain().dispatchOne()) Thread.sleep(5);
        if(f.state()==ProtosFutureValue.State.PENDING)
            throw new AssertionError("timed out waiting for Future");
    }

    private static long deadline(){return System.nanoTime()+WAIT.toNanos();}

    private static final class Fixture implements AutoCloseable {
        final ProtosNioHostIoPoller poller;
        final ServerSocketChannel server;
        final SocketChannel peer;
        final ProtosPrelude prelude;
        final ProtosActivation activation;
        final ProtosTcpConnectionValue connection;

        Fixture(ProtosNioHostIoPoller p,ServerSocketChannel s,SocketChannel q,
                ProtosPrelude pr,ProtosActivation a,ProtosTcpConnectionValue c){
            poller=p;server=s;peer=q;prelude=pr;activation=a;connection=c;
        }

        @Override
        public void close()throws IOException{
            try{peer.close();}
            finally{try{server.close();}finally{poller.close();}}
        }
    }
}
