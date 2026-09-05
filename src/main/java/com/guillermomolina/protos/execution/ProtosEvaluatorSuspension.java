/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. See LICENSE.TXT.
 */
package com.guillermomolina.protos.execution;

/** Host-only transfer that yields a suspended Protos task back to its Actor execution domain. */
public final class ProtosEvaluatorSuspension extends RuntimeException {
    public ProtosEvaluatorSuspension() { super(null, null, false, false); }
}
