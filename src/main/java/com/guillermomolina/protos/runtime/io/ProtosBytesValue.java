/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Arrays;
import java.util.List;
@ExportLibrary(InteropLibrary.class)
public final class ProtosBytesValue extends ProtosObjectValue{
 private static final byte[] EMPTY = new byte[0];
 /* The octets live in the prefix [0, length) of a private primitive array; values never escapes. */
 private byte[] values;
 private int length;

 public ProtosBytesValue(Object parent){super(parent);this.values=EMPTY;}

 /** Bytes holding a defensive copy of {@code source}; the array is never retained. */
 public ProtosBytesValue(Object parent, byte[] source){
  super(parent);
  this.values=Arrays.copyOf(source,source.length);
  this.length=source.length;
 }

 public int indexedSize(){return length;}

 /** The unsigned semantic Integer octet at {@code i}. */
 public Object indexedAt(int i){return ProtosNumericValueSupport.octet(values[index(i)]&0xff);}

 /**
  * Replaces one octet, validating frozen state, then the index, then octet admission, before
  * any write. Answers {@code v} itself.
  */
 public Object indexedPut(int i,Object v){
  if(isFrozen())throw new IllegalStateException("bytes is frozen");
  int existing=index(i);
  byte octet=requireOctet(v);
  values[existing]=octet;
  return v;
 }

 /** Appends one octet after validating open state and octet admission. Answers {@code v} itself. */
 public Object indexedAdd(Object v){
  if(!isOpen())throw new IllegalStateException("bytes is not open");
  byte octet=requireOctet(v);
  ensureCapacity(length+1);
  values[length++]=octet;
  return v;
 }

 public Object indexedRemoveAt(int i){
  if(!isOpen())throw new IllegalStateException("bytes is not open");
  int existing=index(i);
  int removed=values[existing]&0xff;
  System.arraycopy(values,existing+1,values,existing,length-existing-1);
  length--;
  return ProtosNumericValueSupport.octet(removed);
 }

 /** An immutable list of the current octets as semantic Integers, detached from this receiver. */
 public List<Object> indexedSnapshot(){return List.of(indexedSnapshotForRuntime());}

 /** A fresh array of the current octets as semantic Integers; it is not the Bytes storage. */
 public Object[] indexedSnapshotForRuntime() {
    Object[] snapshot = new Object[length];
    for (int index = 0; index < snapshot.length; index++) {
        snapshot[index] = ProtosNumericValueSupport.octet(values[index] & 0xff);
    }
    return snapshot;
 }

 /** A defensive host copy of the current octets. */
 public byte[] octetSnapshot(){return Arrays.copyOf(values,length);}

 /**
  * Runtime population only: appends a physical copy of {@code source} after validating open
  * state and the resulting length; the array is never retained.
  */
 public void appendOctetsForRuntime(byte[] source){
  if(!isOpen())throw new IllegalStateException("bytes is not open");
  if(source.length>Integer.MAX_VALUE-length)throw new IllegalStateException("bytes length overflow");
  ensureCapacity(length+source.length);
  System.arraycopy(source,0,values,length,source.length);
  length+=source.length;
 }

 /* Grows the backing to hold at least capacity octets, keeping the current prefix. */
 private void ensureCapacity(int capacity){
  if(capacity<0)throw new IllegalStateException("bytes length overflow");
  if(capacity<=values.length)return;
  int grown=values.length+(values.length>>1)+16;
  if(grown<capacity||grown<0)grown=capacity;
  values=Arrays.copyOf(values,grown);
 }

 /* The host octet of a semantic Integer in 0..255; anything else fails before any mutation. */
 private static byte requireOctet(Object v){
  if(!ProtosNumericValueSupport.isUnsignedIntegerWithin(v,Byte.SIZE)||!ProtosNumericValueSupport.isIntegerInLongRange(v))throw new IllegalArgumentException("bytes element is not an octet Integer");
  return (byte)ProtosNumericValueSupport.exactLong(v);
 }

 private int index(int i){if(i<0||i>=length)throw new IndexOutOfBoundsException("bytes index out of bounds: "+i);return i;}


    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return indexedSize();
    }

    @ExportMessage
    boolean isArrayElementReadable(long index) {
        if (index < 0 || index >= length) {
            return false;
        }
        final Object value;
        try {
            value = indexedAt((int) index);
        } catch (IndexOutOfBoundsException failure) {
            return false;
        }
        return InteropLibrary.isValidValue(value);
    }

    @ExportMessage
    Object readArrayElement(long index) throws InvalidArrayIndexException {
        if (index < 0 || index >= length) {
            throw InvalidArrayIndexException.create(index);
        }
        final Object value;
        try {
            value = indexedAt((int) index);
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
        return "Bytes";
    }

}
