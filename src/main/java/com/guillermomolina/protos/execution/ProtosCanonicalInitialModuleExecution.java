/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.ProtosActivation;
import com.guillermomolina.protos.runtime.ProtosActorModuleState;
import com.guillermomolina.protos.runtime.ProtosModuleKey;
import com.guillermomolina.protos.runtime.ProtosObjectValue;
import com.guillermomolina.protos.runtime.ProtosPrelude;
import com.oracle.truffle.api.CallTarget;
import java.io.IOException;
import java.util.Objects;
/** Executes one already-canonical initial module in an already-created RootActor context. */
public final class ProtosCanonicalInitialModuleExecution {
 private ProtosCanonicalInitialModuleExecution() {}
 public static ProtosExecutionOutcome execute(ProtosPrelude prelude, ProtosModuleResolver resolver, ProtosModuleKey key, ProtosActivation initialActivation) throws IOException {
  Objects.requireNonNull(prelude,"prelude"); Objects.requireNonNull(resolver,"resolver"); Objects.requireNonNull(key,"key"); Objects.requireNonNull(initialActivation,"initialActivation");
  ProtosActorModuleState state=initialActivation.actorModuleState();
  if(state.lookup(key).isPresent()) throw new IOException("canonical initial module is already cached");
  final CallTarget target;
  try { target=new ProtosSourceCompiler().compile(Objects.requireNonNull(resolver.loadSource(key),"module source")); }
  catch(IOException e){throw e;} catch(Exception e){throw new IOException("canonical initial module preparation failed",e);}
  ProtosObjectValue instance=initialActivation.context(); ProtosActorModuleState.ModuleRecord record=new ProtosActorModuleState.ModuleRecord(instance); state.put(key,record);
  ProtosActivation activation=prelude.newModuleActivation(state,key,instance,initialActivation.executionDomain());
  try { ProtosExecutionOutcome outcome=ProtosRootTaskExecution.execute(target,activation); if(outcome.state()==ProtosExecutionOutcome.State.COMPLETED) record.markReady(); else state.removeIfSame(key,record); return outcome; }
  catch(RuntimeException e){ state.removeIfSame(key,record); throw new IOException("canonical initial module execution failed",e); }
 }
}
