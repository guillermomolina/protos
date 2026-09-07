/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;
import static org.junit.jupiter.api.Assertions.*;
import com.guillermomolina.protos.runtime.*;
import java.nio.file.Path; import java.util.*; import org.junit.jupiter.api.Test;
final class ProtosCanonicalInitialModuleExecutionTest {
 static final Path CORE=Path.of("protos","lib","core"); static final ProtosModuleKey MAIN=new ProtosModuleKey("test:Main");
 @Test void cachesBootstrapContextBeforeExecutingCanonicalEntry() throws Exception {
  ProtosModuleResolver r=new ProtosModuleResolver(){ public ProtosModuleKey resolve(String s,Optional<ProtosModuleKey> i){if(!s.equals("self"))throw new IllegalArgumentException();return MAIN;} public String loadSource(ProtosModuleKey k){return "marker: Object()\nAgain: import(\"self\")\nAgain.marker === marker\n";}};
  ProtosPrelude p=new ProtosCoreBootstrap().bootstrap(CORE,r); var b=bootstrap(p); try {var o=ProtosCanonicalInitialModuleExecution.execute(p,r,MAIN,b.activation()); assertEquals(ProtosExecutionOutcome.State.COMPLETED,o.state()); assertSame(ProtosBooleanValue.TRUE,o.value()); var rec=b.activation().actorModuleState().lookup(MAIN).orElseThrow(); assertEquals(ProtosActorModuleState.InitializationState.READY,rec.state()); assertSame(b.activation().context(),rec.instance());} finally {b.process().requestTerminationForRuntime();}
 }
 @Test void failedInitialModuleIsRemovedFromCache() throws Exception {
  ProtosModuleResolver r=new ProtosModuleResolver(){ public ProtosModuleKey resolve(String s,Optional<ProtosModuleKey> i){return MAIN;} public String loadSource(ProtosModuleKey k){return "Error().signal()\n";}};
  ProtosPrelude p=new ProtosCoreBootstrap().bootstrap(CORE,r); var b=bootstrap(p); try {var o=ProtosCanonicalInitialModuleExecution.execute(p,r,MAIN,b.activation()); assertEquals(ProtosExecutionOutcome.State.FAILED,o.state()); assertTrue(b.activation().actorModuleState().lookup(MAIN).isEmpty());} finally {b.process().requestTerminationForRuntime();}
 }
 static ProtosStandaloneProcessBootstrap.Result bootstrap(ProtosPrelude p){return ProtosStandaloneProcessBootstrap.create(p,List.of(),domain(),List.of(),null,null,null,null,null,null,null);}
 static ProtosEnvironmentValue.NativeNameDomain domain(){return new ProtosEnvironmentValue.NativeNameDomain(){public boolean sameCapturedName(String a,String b){return a.equals(b);} public boolean isQueryRepresentable(String n){return !n.contains("=")&&n.indexOf('\0')<0;} public boolean matchesQuery(String a,String b){return a.equals(b);}};}
}
