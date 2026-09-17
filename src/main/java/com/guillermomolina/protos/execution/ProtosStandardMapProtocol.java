/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.execution;
import com.guillermomolina.protos.runtime.*; import java.math.BigInteger; import java.util.*;
public final class ProtosStandardMapProtocol {
 enum StructuredReadLookupKind { AT, CONTAINS_KEY }

 private static final ProtosNativeClosureBody STANDARD_CALL_BODY =
         ProtosStandardMapProtocol::call;

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
 private static final ProtosNativeClosureBody STANDARD_REMOVE_BODY =
         ProtosStandardMapProtocol::remove;
 private static final ProtosClosureValue STANDARD_REMOVE =
         ProtosClosureValue.nativeClosure(STANDARD_REMOVE_BODY);
 private static final ProtosNativeClosureBody STANDARD_EACH_BODY =
         ProtosStandardMapProtocol::each;
 private static final ProtosClosureValue STANDARD_EACH =
         ProtosClosureValue.nativeClosure(STANDARD_EACH_BODY);
 private static final ProtosNativeClosureBody STANDARD_MATCH_BODY =
         ProtosStandardMapProtocol::match;
 private static final ProtosClosureValue STANDARD_MATCH =
         ProtosClosureValue.nativeClosure(STANDARD_MATCH_BODY);

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

 private static boolean isCanonicalMapHome(
         ProtosObjectValue home,
         ProtosActivation caller) {
  return caller.prelude()
          .map(prelude -> prelude.bindings().readLocalSlot("Map").orElse(null) == home)
          .orElse(false);
 }

 static StructuredReadLookupKind structuredReadLookupKindForCanonicalSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  if (!isCanonicalMapHome(home, caller)) {
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
          && isCanonicalMapHome(home, caller);
 }

 static boolean isStandardRemoveImplementation(ProtosClosureValue closure) {
  return closure.nativeBody().orElse(null) == STANDARD_REMOVE_BODY;
 }

 static boolean isCanonicalStandardRemoveSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  return behavior == STANDARD_REMOVE
          && isCanonicalMapHome(home, caller);
 }

 static boolean isStandardEachImplementation(ProtosClosureValue closure) {
  return closure.nativeBody().orElse(null) == STANDARD_EACH_BODY;
 }

 static boolean isStandardMatchImplementation(ProtosNativeClosureBody body) {
  return body == STANDARD_MATCH_BODY;
 }

 static boolean isCanonicalStandardEachSelection(
         ProtosClosureValue behavior,
         ProtosObjectValue home,
         ProtosActivation caller) {
  return behavior == STANDARD_EACH
          && isCanonicalMapHome(home, caller);
 }
 public static void install(ProtosObjectValue p){
  for(String s:List.of("call","at","atPut","containsKey","remove","size","each","match"))if(p.hasLocalSlot(s))throw new IllegalStateException("Core Map already defines "+s);
  p.createLocalSlot("call",ProtosClosureValue.nativeClosure(STANDARD_CALL_BODY));
  p.createLocalSlot("at", STANDARD_AT);
  p.createLocalSlot("containsKey", STANDARD_CONTAINS_KEY);
  p.createLocalSlot("atPut", STANDARD_AT_PUT);
  p.createLocalSlot("remove", STANDARD_REMOVE);
  p.createLocalSlot("size",ProtosClosureValue.nativeClosure((a,x)->{ProtosMapValue m=map(a);arity(a,x,0);return new ProtosIntegerValue(BigInteger.valueOf(m.keyedSize()));}));
  p.createLocalSlot("each", STANDARD_EACH);
  p.createLocalSlot("match", STANDARD_MATCH);
 }
 static ProtosSlotLookupResult selectFactoryCallForMapConstruction(
         Object factory,
         ProtosActivation caller) {
  ProtosPrelude prelude = caller.prelude().orElseThrow();
  ProtosSlotLookupResult selected;
  try {
   selected = ProtosValueLookup.lookup(factory, "call", prelude)
           .orElseThrow(() -> err(caller));
  } catch (UnsupportedOperationException unsupportedRepresentation) {
   throw err(caller);
  }
  if (!(selected.value() instanceof ProtosClosureValue behavior)
          || behavior.nativeBody().orElse(null) != STANDARD_CALL_BODY
          || !isCanonicalMapHome(selected.home(), caller)) {
   throw err(caller);
  }
  return selected;
 }

 static ProtosMapValue requireMapConstructionResult(
         Object result,
         ProtosActivation caller) {
  if (!(result instanceof ProtosMapValue map)) {
   throw err(caller);
  }
  return map;
 }

 static ProtosMapValue constructForMapConstruction(
         Object factory,
         ProtosActivation caller) {
  ProtosSlotLookupResult selected =
          selectFactoryCallForMapConstruction(factory, caller);
  ProtosClosureValue behavior =
          (ProtosClosureValue) selected.value();
  Object result =
          ProtosClosureInvoker.invokeImmediateMethod(
                  behavior,
                  factory,
                  selected.home(),
                  List.of(),
                  caller);
  return requireMapConstructionResult(result, caller);
 }

 static void defineInitialAssociationForMapConstruction(
         ProtosMapValue map,
         Object key,
         Object value,
         ProtosActivation caller) {
  if (!map.isOpen() || map.comparisonActive()) {
   throw err(caller);
  }
  BigInteger recordedHash = hash(map, key, caller);
  if (find(map, key, recordedHash, caller) != null) {
   throw err(caller);
  }
  if (!map.isOpen() || map.comparisonActive()) {
   throw err(caller);
  }
  map.append(key, recordedHash, value);
 }

 private static Object call(ProtosActivation a, List<?> x) {
  arity(a, x, 0);
  Object canonical =
          a.prelude()
                  .map(prelude -> prelude.bindings().readLocalSlot("Map").orElse(null))
                  .orElse(null);
  if (!(canonical instanceof ProtosObjectValue p)
          || !(a.receiver() instanceof ProtosObjectValue r)
          || !delegatesTo(r, p)) {
   throw err(a);
  }
  return new ProtosMapValue(r);
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
 private static Object remove(ProtosActivation a, List<?> x) {
  ProtosMapValue m = map(a);
  arity(a, x, 1);
  mutationEntry(m, a);
  if (!m.isOpen()) throw err(a);
  ProtosMapValue.Entry e = find(m, x.get(0), a);
  if (e == null || !m.isOpen()) throw err(a);
  return m.remove(e);
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

 private static Object match(ProtosActivation a, List<?> x) {
  ProtosMapValue matcher = map(a);
  arity(a, x, 1);

  Object subjectValue = x.get(0);
  if (!(subjectValue instanceof ProtosMapValue subject)) {
   return ProtosBooleanValue.FALSE;
  }

  List<StableAssociation> matcherSnapshot = stableSnapshot(matcher);
  List<StableAssociation> subjectSnapshot = stableSnapshot(subject);

  List<Object> childMatchers = new ArrayList<>();
  List<Object> selectedValues = new ArrayList<>();

  for (StableAssociation requirement : matcherSnapshot) {
   BigInteger queryHash = queryHash(subject, requirement.key(), a);
   int subjectIndex =
           findStableAssociationIndex(
                   subject,
                   subjectSnapshot,
                   requirement.key(),
                   queryHash,
                   a);
   if (subjectIndex < 0) {
    return ProtosBooleanValue.FALSE;
   }

   childMatchers.add(requirement.value());
   selectedValues.add(subjectSnapshot.get(subjectIndex).value());
  }

  List<Object> captures = new ArrayList<>();

  for (int index = 0; index < childMatchers.size(); index++) {
   Object outcome =
           ProtosInvocation.invokeMessage(
                   childMatchers.get(index),
                   "match",
                   List.of(selectedValues.get(index)),
                   a);

   if (outcome == ProtosBooleanValue.FALSE) {
    return ProtosBooleanValue.FALSE;
   }

   if (outcome == ProtosBooleanValue.TRUE) {
    continue;
   }

   if (outcome instanceof ProtosArrayValue childCaptures) {
    List<Object> observedCaptures = childCaptures.indexedSnapshot();
    if (observedCaptures.isEmpty()) {
     throw err(a);
    }
    captures.addAll(observedCaptures);
    continue;
   }

   throw err(a);
  }

  if (captures.isEmpty()) {
   return ProtosBooleanValue.TRUE;
  }

  return a.prelude()
          .orElseThrow(
                  () ->
                          new IllegalStateException(
                                  "standard Map.match requires an owning Core prelude"))
          .newArray(captures);
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
