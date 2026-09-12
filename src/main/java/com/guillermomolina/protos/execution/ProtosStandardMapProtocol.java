/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger; import java.util.*;
public final class ProtosStandardMapProtocol {
 enum StructuredReadLookupKind { AT, CONTAINS_KEY }

 private static final ProtosNativeClosureBody STANDARD_AT_BODY =
         ProtosStandardMapProtocol::at;
 private static final ProtosClosureValue STANDARD_AT =
         ProtosClosureValue.nativeClosure(STANDARD_AT_BODY);
 private static final ProtosNativeClosureBody STANDARD_AT_PUT_BODY =
         ProtosStandardMapProtocol::atPut;
 private static final ProtosClosureValue STANDARD_AT_PUT =
         ProtosClosureValue.nativeClosure(STANDARD_AT_PUT_BODY);
 private static final ProtosNativeClosureBody STANDARD_CONTAINS_KEY_BODY =
         ProtosStandardMapProtocol::containsKey;
 private static final ProtosClosureValue STANDARD_CONTAINS_KEY =
         ProtosClosureValue.nativeClosure(STANDARD_CONTAINS_KEY_BODY);
 private static final ProtosNativeClosureBody STANDARD_EACH_BODY =
         ProtosStandardMapProtocol::each;
 private static final ProtosClosureValue STANDARD_EACH =
         ProtosClosureValue.nativeClosure(STANDARD_EACH_BODY);

 private ProtosStandardMapProtocol(){}

 static record StableAssociation(
         Object key,
         BigInteger recordedHash,
         Object value) {
  StableAssociation {
   Objects.requireNonNull(key, "key");
   Objects.requireNonNull(recordedHash, "recordedHash");
   Objects.requireNonNull(value, "value");
  }
 }

 static List<StableAssociation> stableSnapshot(ProtosMapValue map) {
  Objects.requireNonNull(map, "map");
  return map.keyedSnapshot().stream()
          .map(entry -> new StableAssociation(
                  entry.key(),
                  entry.recordedHash(),
                  entry.value()))
          .toList();
 }

 static BigInteger queryHash(
         ProtosMapValue map,
         Object queryKey,
         ProtosActivation activation) {
  return hash(map, queryKey, activation);
 }

 static int findStableAssociationIndex(
         ProtosMapValue map,
         List<StableAssociation> snapshot,
         Object queryKey,
         BigInteger queryHash,
         ProtosActivation activation) {
  for (int index = 0; index < snapshot.size(); index++) {
   StableAssociation association = snapshot.get(index);
   if (matchesStoredKey(
           map,
           queryKey,
           queryHash,
           association.key(),
           association.recordedHash(),
           activation)) {
    return index;
   }
  }
  return -1;
 }

 static StructuredReadLookupKind structuredReadLookupKindForImplementation(
         ProtosClosureValue closure) {
  ProtosNativeClosureBody body = closure.nativeBody().orElse(null);
  if (body == STANDARD_AT_BODY) {
   return StructuredReadLookupKind.AT;
  }
  if (body == STANDARD_CONTAINS_KEY_BODY) {
   return StructuredReadLookupKind.CONTAINS_KEY;
  }
  return null;
 }

 static StructuredReadLookupKind structuredReadLookupKindForCanonicalSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  if (!caller.prelude().map(prelude -> prelude.mapPrototype() == home).orElse(false)) {
   return null;
  }
  if (behavior == STANDARD_AT) {
   return StructuredReadLookupKind.AT;
  }
  if (behavior == STANDARD_CONTAINS_KEY) {
   return StructuredReadLookupKind.CONTAINS_KEY;
  }
  return null;
 }

 static boolean isStandardAtPutImplementation(ProtosClosureValue closure) {
  return closure.nativeBody().orElse(null) == STANDARD_AT_PUT_BODY;
 }

 static boolean isCanonicalStandardAtPutSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  return behavior == STANDARD_AT_PUT
          && caller.prelude()
                  .map(prelude -> prelude.mapPrototype() == home)
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
                  .map(prelude -> prelude.mapPrototype() == home)
                  .orElse(false);
 }
 public static void install(ProtosObjectValue p){
  for(String s:List.of("call","at","atPut","containsKey","remove","size","each"))if(p.hasLocalSlot(s))throw new IllegalStateException("Core Map already defines "+s);
  p.createLocalSlot("call",ProtosClosureValue.nativeClosure((a,x)->{arity(a,x,0);if(!(a.receiver() instanceof ProtosObjectValue r)||!delegatesTo(r,p))throw err(a);return new ProtosMapValue(r);}));
  p.createLocalSlot("at", STANDARD_AT);
  p.createLocalSlot("containsKey", STANDARD_CONTAINS_KEY);
  p.createLocalSlot("atPut", STANDARD_AT_PUT);
  p.createLocalSlot("remove",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,1);mutationEntry(m,a);if(!m.isOpen())throw err(a);var e=find(m,x.get(0),a);if(e==null||!m.isOpen())throw err(a);return m.remove(e);}));
  p.createLocalSlot("size",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,0);return new ProtosIntegerValue(BigInteger.valueOf(m.keyedSize()));}));
  p.createLocalSlot("each", STANDARD_EACH);
 }
 private static Object at(ProtosActivation a, List<?> x) {
  ProtosMapValue m = map(a);
  arity(a, x, 1);
  ProtosMapValue.Entry entry = find(m, x.get(0), a);
  if (entry == null) {
   throw err(a);
  }
  return entry.value();
 }
 private static Object atPut(ProtosActivation a, List<?> x) {
  ProtosMapValue m = map(a);
  arity(a, x, 2);
  mutationEntry(m, a);
  Object k = x.get(0);
  Object v = x.get(1);
  BigInteger h = hash(m, k, a);
  ProtosMapValue.Entry e = find(m, k, h, a);
  if (e != null) {
   if (m.isFrozen()) throw err(a);
   m.replaceValue(e, v);
   return v;
  }
  if (!m.isOpen()) throw err(a);
  m.append(k, h, v);
  return v;
 }
 private static Object containsKey(ProtosActivation a, List<?> x) {
  ProtosMapValue m = map(a);
  arity(a, x, 1);
  return find(m, x.get(0), a) == null
          ? ProtosBooleanValue.FALSE
          : ProtosBooleanValue.TRUE;
 }
 private static Object each(ProtosActivation a, List<?> x) {
  ProtosMapValue m = map(a);
  arity(a, x, 1);
  Object block = x.get(0);
  requireInvokableForStructured(block, a);
  for (var entry : m.associationSnapshot()) {
   ProtosInvocation.invoke(block, List.of(entry.getKey(), entry.getValue()), a);
  }
  return m;
 }
 private static ProtosMapValue.Entry find(ProtosMapValue m,Object k,ProtosActivation a){
  return find(m,k,hash(m,k,a),a);
 }
 private static ProtosMapValue.Entry find(ProtosMapValue m,Object k,BigInteger h,ProtosActivation a){
  for(var e:m.keyedSnapshot()){
   if(matchesStoredKey(m,k,h,e.key(),e.recordedHash(),a))return e;
  }
  return null;
 }
 private static boolean matchesStoredKey(
         ProtosMapValue map,
         Object queryKey,
         BigInteger queryHash,
         Object storedKey,
         BigInteger recordedHash,
         ProtosActivation activation) {
  if(!recordedHash.equals(queryHash))return false;
  map.enterComparison();
  Object comparison;
  try{
   comparison=ProtosInvocation.invokeMessage(
           queryKey,
           "==",
           List.of(storedKey),
           activation);
  }finally{
   map.leaveComparison();
  }
  return requireEqualityResultForStructured(comparison, activation);
 }
 private static BigInteger hash(ProtosMapValue m,Object k,ProtosActivation a){m.enterComparison();Object h;try{h=ProtosInvocation.invokeMessage(k,"hash",List.of(),a);}finally{m.leaveComparison();}return requireHashResultForStructured(h,a);}
 static BigInteger requireHashResultForStructured(Object h, ProtosActivation a) {
  if (h instanceof ProtosIntegerValue i) {
   return i.value();
  }
  if (h instanceof ProtosFixedIntegerValue i) {
   return i.value();
  }
  throw err(a);
 }
 static boolean requireEqualityResultForStructured(Object q, ProtosActivation a) {
  if (q == ProtosBooleanValue.TRUE) {
   return true;
  }
  if (q == ProtosBooleanValue.FALSE) {
   return false;
  }
  throw err(a);
 }
 private static void mutationEntry(ProtosMapValue m, ProtosActivation a) {
  requireMutationEntryForStructured(m, a);
 }
 static void requireMutationEntryForStructured(
         ProtosMapValue m, ProtosActivation a) {
  if (m.comparisonActive() || m.isFrozen()) throw err(a);
 }
 private static ProtosMapValue map(ProtosActivation a){if(!(a.receiver() instanceof ProtosMapValue m))throw err(a);return m;}
 private static void arity(ProtosActivation a,List<?> x,int n){if(x.size()!=n)throw err(a);}
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
 private static boolean delegatesTo(ProtosObjectValue r,ProtosObjectValue p){Object c=r;while(c instanceof ProtosObjectValue o){if(o==p)return true;c=o.parent().orElse(null);}return false;}
 private static ProtosSignalException err(ProtosActivation a){return new ProtosSignalException(ProtosCoreErrors.newError(a));}
}
