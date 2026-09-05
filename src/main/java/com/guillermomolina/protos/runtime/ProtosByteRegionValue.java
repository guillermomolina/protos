/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;
import java.math.BigInteger;import java.util.*;
public final class ProtosByteRegionValue extends ProtosObjectValue{
 private final List<Object> bytes;private final List<R> reservations=new ArrayList<>();
 private record R(BigInteger s,BigInteger e,Object token){}
 public ProtosByteRegionValue(List<?> v){super(ProtosObjectValue.rootObject());bytes=new ArrayList<>(v);}
 public synchronized BigInteger indexedSize(){return BigInteger.valueOf(bytes.size());}
 public synchronized Object indexedAt(BigInteger i){return bytes.get(index(i));}
 public synchronized Object indexedPut(BigInteger i,Object v){bytes.set(index(i),Objects.requireNonNull(v));return v;}
 public synchronized List<Object> indexedSnapshot(){return List.copyOf(bytes);}
 public synchronized List<Object> rangeSnapshot(BigInteger s,BigInteger l){return List.copyOf(bytes.subList(s.intValueExact(),s.add(l).intValueExact()));}
 public synchronized boolean tryReserve(BigInteger s,BigInteger l,Object t){if(l.signum()==0)return true;BigInteger e=s.add(l);for(R r:reservations)if(s.compareTo(r.e)<0&&r.s.compareTo(e)<0)return false;reservations.add(new R(s,e,t));return true;}
 public synchronized void releaseReservation(Object t){reservations.removeIf(r->r.token==t);}
 public synchronized boolean isIndexReserved(BigInteger i){for(R r:reservations)if(i.compareTo(r.s)>=0&&i.compareTo(r.e)<0)return true;return false;}
 public synchronized void commitReserved(BigInteger s,List<?> v,Object t){for(int i=0;i<v.size();i++)bytes.set(s.intValueExact()+i,v.get(i));releaseReservation(t);}
 private int index(BigInteger i){if(i.signum()<0||i.compareTo(BigInteger.valueOf(bytes.size()))>=0)throw new IndexOutOfBoundsException();return i.intValueExact();}
}
