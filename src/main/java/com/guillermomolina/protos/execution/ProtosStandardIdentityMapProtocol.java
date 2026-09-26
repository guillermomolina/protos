/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger; import java.util.*;
public final class ProtosStandardIdentityMapProtocol {
 private static final class StandardAtIfAbsentBody implements ProtosNativeClosureBody {
  @Override
  public Object execute(ProtosActivation activation, List<?> arguments) {
   return atIfAbsent(activation, arguments);
  }
 }

 private static final ProtosNativeClosureBody STANDARD_AT_IF_ABSENT_BODY =
         new StandardAtIfAbsentBody();
 private static final ProtosClosureValue STANDARD_AT_IF_ABSENT =
         ProtosClosureValue.nativeClosure(STANDARD_AT_IF_ABSENT_BODY);
 private static final ProtosNativeClosureBody STANDARD_EACH_BODY =
         ProtosStandardIdentityMapProtocol::each;
 private static final ProtosClosureValue STANDARD_EACH =
         ProtosClosureValue.nativeClosure(STANDARD_EACH_BODY);

 private ProtosStandardIdentityMapProtocol(){}

 static boolean isStandardAtIfAbsentImplementation(ProtosClosureValue closure) {
  return closure.nativeBody().orElse(null) == STANDARD_AT_IF_ABSENT_BODY;
 }

 static boolean isStandardAtIfAbsentImplementation(
         ProtosNativeClosureBody body) {
  return body instanceof StandardAtIfAbsentBody;
 }

 static boolean isCanonicalStandardAtIfAbsentSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  return behavior == STANDARD_AT_IF_ABSENT
          && caller.prelude()
                  .map(prelude -> prelude.identityMapPrototype() == home)
                  .orElse(false);
 }

 static boolean isStandardEachImplementation(ProtosClosureValue closure) {
  return closure.nativeBody().orElse(null) == STANDARD_EACH_BODY;
 }

 static boolean isCanonicalStandardEachSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  return behavior == STANDARD_EACH
          && caller.prelude()
                  .map(prelude -> prelude.identityMapPrototype() == home)
                  .orElse(false);
 }
 public static void install(ProtosObjectValue p){
  for(String s:List.of("call","at","atPut","containsKey","atIfAbsent","remove","size","each"))if(p.hasLocalSlot(s))throw new IllegalStateException("Core IdentityMap already defines "+s);
  p.createLocalSlot("call",ProtosClosureValue.nativeClosure((a,x)->{arity(a,x,0);if(!(a.receiver() instanceof ProtosObjectValue r)||!delegatesTo(r,p))throw err(a);return new ProtosIdentityMapValue(r);}));
  p.createLocalSlot("at",ProtosClosureValue.nativeClosure((a,x)->{var m=map(a);arity(a,x,1);var e=find(m,x.get(0));if(e==null)throw err(a);return e.value();}));
  p.createLocalSlot("containsKey",ProtosClosureValue.nativeClosure((a,x)->{var m=map(a);arity(a,x,1);return find(m,x.get(0))==null?ProtosBooleanValue.FALSE:ProtosBooleanValue.TRUE;}));
  p.createLocalSlot("atIfAbsent", STANDARD_AT_IF_ABSENT);
  p.createLocalSlot("atPut",ProtosClosureValue.nativeClosure((a,x)->{var m=map(a);arity(a,x,2);if(m.isFrozen())throw err(a);Object k=x.get(0),v=x.get(1);var e=find(m,k);if(e!=null){m.replaceValue(e,v);return v;}if(!m.isOpen())throw err(a);m.append(k,ProtosIdentity.identityHash(k),v);return v;}));
  p.createLocalSlot("remove",ProtosClosureValue.nativeClosure((a,x)->{var m=map(a);arity(a,x,1);if(!m.isOpen())throw err(a);var e=find(m,x.get(0));if(e==null)throw err(a);return m.remove(e);}));
  p.createLocalSlot("size",ProtosClosureValue.nativeClosure((a,x)->{var m=map(a);arity(a,x,0);return new ProtosIntegerValue(BigInteger.valueOf(m.keyedSize()));}));
  p.createLocalSlot("each", STANDARD_EACH);
 }
 private static Object atIfAbsent(ProtosActivation a, List<?> x) {
  var m = map(a);
  arity(a, x, 2);
  var entry = find(m, x.get(0));
  if (entry != null) {
   return entry.value();
  }
  return ProtosInvocation.invoke(x.get(1), List.of(), a);
 }
 private static Object each(ProtosActivation a, List<?> x) {
  var m = map(a);
  arity(a, x, 1);
  Object block = x.get(0);
  requireInvokableForStructured(block, a);
  for (var entry : m.associationSnapshot()) {
   ProtosInvocation.invoke(block, List.of(entry.getKey(), entry.getValue()), a);
  }
  return m;
 }
 static ProtosIdentityMapValue.Entry findForStructured(
         ProtosIdentityMapValue map,
         Object key) {
  return find(map, key);
 }

 private static ProtosIdentityMapValue.Entry find(ProtosIdentityMapValue m,Object k){BigInteger h=ProtosIdentity.identityHash(k);for(var e:m.keyedSnapshot())if(e.recordedIdentityHash().equals(h)&&ProtosIdentity.identical(k,e.key()))return e;return null;}
 private static ProtosIdentityMapValue map(ProtosActivation a){if(!(a.receiver() instanceof ProtosIdentityMapValue m))throw err(a);return m;} private static void arity(ProtosActivation a,List<?> x,int n){if(x.size()!=n)throw err(a);}
 static void requireInvokableForStructured(Object c, ProtosActivation a) {
  ProtosPrelude p = a.prelude().orElseThrow();
  ProtosSlotLookupResult selected;
  try {
   selected = ProtosValueLookup.lookup(c, "call", p).orElseThrow(() -> err(a));
  } catch (UnsupportedOperationException unsupportedRepresentation) {
   throw err(a);
  }
  if (!(selected.value() instanceof ProtosClosureValue)) {
   throw err(a);
  }
 }
 private static boolean delegatesTo(ProtosObjectValue r,ProtosObjectValue p){Object c=r;while(c instanceof ProtosObjectValue o){if(o==p)return true;c=o.parent().orElse(null);}return false;} private static ProtosSignalException err(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
}
