/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;
import java.math.BigInteger; import java.util.*;
public final class ProtosIdentityMapValue extends ProtosObjectValue {
 public static final class Entry { private final Object key; private final BigInteger hash; private Object value; Entry(Object k,BigInteger h,Object v){key=Objects.requireNonNull(k);hash=Objects.requireNonNull(h);value=Objects.requireNonNull(v);} public Object key(){return key;} public BigInteger recordedIdentityHash(){return hash;} public Object value(){return value;} void value(Object v){value=Objects.requireNonNull(v);} }
 private final List<Entry> entries=new ArrayList<>(); public ProtosIdentityMapValue(Object parent){super(parent);} public int keyedSize(){return entries.size();} public List<Entry> keyedSnapshot(){return List.copyOf(entries);} public void append(Object k,BigInteger h,Object v){entries.add(new Entry(k,h,v));} public void replaceValue(Entry e,Object v){e.value(v);} public Object remove(Entry e){if(!entries.remove(e))throw new IllegalStateException("foreign IdentityMap entry");return e.value();}
}
