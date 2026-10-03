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
public final class ProtosBytesValue extends ProtosObjectValue{
 private final List<Object> octets=new ArrayList<>();
 public ProtosBytesValue(Object parent){super(parent);}
 public BigInteger indexedSize(){return BigInteger.valueOf(octets.size());}
 public Object indexedAt(BigInteger i){return octets.get(index(i));}
 public Object indexedPut(BigInteger i,Object v){if(isFrozen())throw new IllegalStateException("bytes is frozen");octets.set(index(i),Objects.requireNonNull(v));return v;}
 public Object indexedAdd(Object v){if(!isOpen())throw new IllegalStateException("bytes is not open");octets.add(Objects.requireNonNull(v));return v;}
 public Object indexedRemoveAt(BigInteger i){if(!isOpen())throw new IllegalStateException("bytes is not open");return octets.remove(index(i));}
 public List<Object> indexedSnapshot(){return List.copyOf(octets);}
 private int index(BigInteger i){Objects.requireNonNull(i);if(i.signum()<0||i.compareTo(BigInteger.valueOf(octets.size()))>=0)throw new IndexOutOfBoundsException("bytes index out of bounds: "+i);return i.intValueExact();}


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
        return "Bytes";
    }

}
