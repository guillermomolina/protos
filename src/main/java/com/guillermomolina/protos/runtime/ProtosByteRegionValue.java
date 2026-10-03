/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.math.BigInteger;import java.util.*;
@ExportLibrary(InteropLibrary.class)
public final class ProtosByteRegionValue extends ProtosObjectValue{
 private final List<Object> bytes;
 /*
  * Immutable reservation snapshots are published through this volatile field.
  * Ordinary owner execution is serialized with first-reservation establishment;
  * only completion of an already-active P region can race with later owner access.
  */
 private volatile List<R> reservations;
 private record R(BigInteger s,BigInteger e,Object token){}
 public ProtosByteRegionValue(List<?> v){super(ProtosObjectValue.rootObject());bytes=new ArrayList<>(v);}
 public BigInteger indexedSize(){return BigInteger.valueOf(bytes.size());}
 public Object indexedAt(BigInteger i){return bytes.get(index(i));}
 public Object indexedPut(BigInteger i,Object v){bytes.set(index(i),Objects.requireNonNull(v));return v;}
 public List<Object> indexedSnapshot(){
  if(reservations==null)return List.copyOf(bytes);
  synchronized(this){return List.copyOf(bytes);}
 }
 public List<Object> rangeSnapshot(BigInteger s,BigInteger l){
  if(reservations==null)return List.copyOf(bytes.subList(s.intValueExact(),s.add(l).intValueExact()));
  synchronized(this){return List.copyOf(bytes.subList(s.intValueExact(),s.add(l).intValueExact()));}
 }
 public synchronized boolean tryReserve(BigInteger s,BigInteger l,Object t){
  if(l.signum()==0)return true;
  BigInteger e=s.add(l);
  List<R> current=reservations;
  if(current!=null)for(R r:current)if(s.compareTo(r.e)<0&&r.s.compareTo(e)<0)return false;
  ArrayList<R> updated=current==null?new ArrayList<>(1):new ArrayList<>(current);
  updated.add(new R(s,e,t));
  reservations=List.copyOf(updated);
  return true;
 }
 public synchronized void releaseReservation(Object t){releaseReservationLocked(t);}
 public boolean isIndexReserved(BigInteger i){
  List<R> current=reservations;
  if(current==null)return false;
  for(R r:current)if(i.compareTo(r.s)>=0&&i.compareTo(r.e)<0)return true;
  return false;
 }
 public synchronized void commitReserved(BigInteger s,List<?> v,Object t){
  for(int i=0;i<v.size();i++)bytes.set(s.intValueExact()+i,v.get(i));
  releaseReservationLocked(t);
 }
 private void releaseReservationLocked(Object t){
  List<R> current=reservations;
  if(current==null)return;
  ArrayList<R> updated=null;
  for(int index=current.size()-1;index>=0;index--){
   if(current.get(index).token==t){
    if(updated==null)updated=new ArrayList<>(current);
    updated.remove(index);
   }
  }
  if(updated!=null)reservations=updated.isEmpty()?null:List.copyOf(updated);
 }
 private int index(BigInteger i){if(i.signum()<0||i.compareTo(BigInteger.valueOf(bytes.size()))>=0)throw new IndexOutOfBoundsException();return i.intValueExact();}


    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return indexedSize().longValueExact();
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        if (index < 0) {
            return false;
        }
        final Object value;
        try {
            value = indexedAt(BigInteger.valueOf(index));
        } catch (IndexOutOfBoundsException failure) {
            return false;
        }
        return InteropLibrary.isValidValue(value);
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (index < 0) {
            throw InvalidArrayIndexException.create(index);
        }
        final Object value;
        try {
            value = indexedAt(BigInteger.valueOf(index));
        } catch (IndexOutOfBoundsException failure) {
            throw InvalidArrayIndexException.create(index);
        }
        if (!InteropLibrary.isValidValue(value)) {
            throw InvalidArrayIndexException.create(index);
        }
        return value;
    }

    @ExportMessage
    boolean hasIterator() {
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object getIterator() throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @Override
    @ExportMessage
    String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "ByteRegion";
    }

}
