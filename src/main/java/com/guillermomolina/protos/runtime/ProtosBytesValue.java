/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.*;
@ExportLibrary(InteropLibrary.class)
public final class ProtosBytesValue extends ProtosObjectValue{
 private final List<Object> octets=new ArrayList<>();
 public ProtosBytesValue(Object parent){super(parent);}
 public int indexedSize(){return octets.size();}
 public Object indexedAt(int i){return octets.get(index(i));}
 public Object indexedPut(int i,Object v){if(isFrozen())throw new IllegalStateException("bytes is frozen");octets.set(index(i),Objects.requireNonNull(v));return v;}
 public Object indexedAdd(Object v){if(!isOpen())throw new IllegalStateException("bytes is not open");octets.add(Objects.requireNonNull(v));return v;}
 public Object indexedRemoveAt(int i){if(!isOpen())throw new IllegalStateException("bytes is not open");return octets.remove(index(i));}
 public List<Object> indexedSnapshot(){return List.copyOf(octets);}
 /**
  * Host octet copy of the current content. Every element is an Integer in 0..255 by the Bytes
  * invariant, so a violation is an internal failure rather than a guest-visible outcome.
  */
 public byte[] octetSnapshot(){
  byte[] result=new byte[octets.size()];
  for(int i=0;i<result.length;i++){
   if(!(octets.get(i) instanceof ProtosIntegerValue octet)||!octet.fitsUnsignedBitsForRuntime(Byte.SIZE))throw new IllegalStateException("Bytes invariant violated");
   result[i]=(byte)octet.longValue();
  }
  return result;
 }

 private int index(int i){if(i<0||i>=octets.size())throw new IndexOutOfBoundsException("bytes index out of bounds: "+i);return i;}


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
        if (index < 0 || index >= octets.size()) {
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
        if (index < 0 || index >= octets.size()) {
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
