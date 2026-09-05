/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;
import java.math.BigInteger;import java.util.*;
public final class ProtosBytesValue extends ProtosObjectValue{
 private final List<Object> octets=new ArrayList<>();private final List<R> reservations=new ArrayList<>();
 private record R(BigInteger s,BigInteger e,Object token){}
 public ProtosBytesValue(Object parent){super(parent);}
 public synchronized BigInteger indexedSize(){return BigInteger.valueOf(octets.size());}
 public synchronized Object indexedAt(BigInteger i){return octets.get(index(i));}
 public synchronized Object indexedPut(BigInteger i,Object v){if(isFrozen())throw new IllegalStateException("bytes is frozen");octets.set(index(i),Objects.requireNonNull(v));return v;}
 public synchronized Object indexedAdd(Object v){if(!isOpen())throw new IllegalStateException("bytes is not open");octets.add(Objects.requireNonNull(v));return v;}
 public synchronized Object indexedRemoveAt(BigInteger i){if(!isOpen())throw new IllegalStateException("bytes is not open");return octets.remove(index(i));}
 public synchronized List<Object> indexedSnapshot(){return List.copyOf(octets);}
 public synchronized List<Object> rangeSnapshot(BigInteger s,BigInteger l){return List.copyOf(octets.subList(s.intValueExact(),s.add(l).intValueExact()));}
 public synchronized boolean tryReserve(BigInteger s,BigInteger l,Object t){if(l.signum()==0)return true;BigInteger e=s.add(l);for(R r:reservations)if(s.compareTo(r.e)<0&&r.s.compareTo(e)<0)return false;reservations.add(new R(s,e,t));return true;}
 public synchronized void releaseReservation(Object t){reservations.removeIf(r->r.token==t);}
 public synchronized boolean hasReservation(){return !reservations.isEmpty();}
 public synchronized boolean isIndexReserved(BigInteger i){for(R r:reservations)if(i.compareTo(r.s)>=0&&i.compareTo(r.e)<0)return true;return false;}
 public synchronized void commitReserved(BigInteger s,List<?> v,Object t){for(int i=0;i<v.size();i++)octets.set(s.intValueExact()+i,v.get(i));releaseReservation(t);}
 private int index(BigInteger i){Objects.requireNonNull(i);if(i.signum()<0||i.compareTo(BigInteger.valueOf(octets.size()))>=0)throw new IndexOutOfBoundsException("bytes index out of bounds: "+i);return i.intValueExact();}
}
