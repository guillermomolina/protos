/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

/** Host-only transfer for cooperative task cancellation unwind. */
public final class ProtosTaskCancellationException extends RuntimeException {
    public ProtosTaskCancellationException() { super(null, null, false, false); }
}
