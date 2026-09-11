/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

import com.oracle.truffle.api.nodes.ControlFlowException;

/** Backend-private control transfer for cooperative Task cancellation unwind. */
public final class ProtosTaskCancellationException extends ControlFlowException {
    public ProtosTaskCancellationException() {}
}
