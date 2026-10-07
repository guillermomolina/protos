/*
 * THE LICENSED WORK IS PROVIDED UNDER THE TERMS OF THE ADAPTIVE PUBLIC LICENSE
 * ("LICENSE") AS FIRST COMPLETED BY: Guillermo Adrián Molina. ANY USE, PUBLIC
 * DISPLAY, PUBLIC PERFORMANCE, REPRODUCTION OR DISTRIBUTION OF, OR PREPARATION OF
 * DERIVATIVE WORKS BASED ON, THE LICENSED WORK CONSTITUTES RECIPIENT'S ACCEPTANCE
 * OF THIS LICENSE AND ITS TERMS, WHETHER OR NOT SUCH RECIPIENT READS THE TERMS OF
 * THE LICENSE. "LICENSED WORK" AND "RECIPIENT" ARE DEFINED IN THE LICENSE. A COPY
 * OF THE LICENSE IS LOCATED IN THE TEXT FILE ENTITLED "LICENSE.TXT" ACCOMPANYING
 * THE CONTENTS OF THIS FILE. IF A COPY OF THE LICENSE DOES NOT ACCOMPANY THIS
 * FILE, A COPY OF THE LICENSE MAY ALSO BE OBTAINED AT THE FOLLOWING WEB SITE:
 * https://github.com/guillermomolina/protos
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 */
package com.guillermomolina.protos.execution;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately inert host-Java classes for the real {@code java:} provider tests. They reach no
 * I/O, network, process, native, environment, reflection, or class-loading facility. Java equality
 * is deliberately total so that any leak of {@code equals}/{@code hashCode} into Protos identity is
 * observable.
 */
public final class ProtosHostJavaFixtureTypes {
    /** Counts real Java member executions so pre-invocation rejection is observable. */
    static final AtomicInteger INVOCATIONS = new AtomicInteger();

    /** Counts static initialization of {@link Lazy} and {@link Denied}. */
    static final AtomicInteger LAZY_INITIALIZATIONS = new AtomicInteger();

    static final AtomicInteger DENIED_INITIALIZATIONS = new AtomicInteger();

    private ProtosHostJavaFixtureTypes() {}

    public static final class Greeter {
        private final String name;
        private int counter;

        public Greeter(String name) {
            INVOCATIONS.incrementAndGet();
            this.name = name;
        }

        public static Greeter createNamed(String name) {
            return new Greeter(name);
        }

        public static String staticEcho(String value) {
            INVOCATIONS.incrementAndGet();
            return value;
        }

        public static long add(long left, int right) {
            INVOCATIONS.incrementAndGet();
            return left + right;
        }

        public static double half(double value) {
            return value / 2;
        }

        public static float widen(float value) {
            INVOCATIONS.incrementAndGet();
            return value;
        }

        public static boolean negate(boolean value) {
            return !value;
        }

        public static BigInteger square(BigInteger value) {
            return value.multiply(value);
        }

        public static byte tiny(byte value) {
            INVOCATIONS.incrementAndGet();
            return value;
        }

        public static String nothing() {
            return null;
        }

        public static String fail(String detail) {
            throw new IllegalStateException("SECRET " + detail);
        }

        public static int overloaded(int value) {
            INVOCATIONS.incrementAndGet();
            return value;
        }

        public static int overloaded(String value) {
            INVOCATIONS.incrementAndGet();
            return value.length();
        }

        /** Present and public but never exposed by the test catalogue. */
        public static String hidden() {
            INVOCATIONS.incrementAndGet();
            return "hidden";
        }

        public String name() {
            return name;
        }

        public String join(String suffix) {
            return name + suffix;
        }

        public Greeter self() {
            return this;
        }

        public boolean sameAs(Greeter other) {
            return other == this;
        }

        public int increment() {
            return ++counter;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Greeter;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    /** An admitted class whose static initialization must stay lazy until first real use. */
    public static final class Lazy {
        static {
            LAZY_INITIALIZATIONS.incrementAndGet();
        }

        public static String ping() {
            return "pong";
        }
    }

    /** Loadable on the classpath but never admitted: it must never be initialized by an import. */
    public static final class Denied {
        static {
            DENIED_INITIALIZATIONS.incrementAndGet();
        }

        public static String ping() {
            return "denied";
        }
    }
}
