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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable embedder-supplied catalogue of the exact host-Java surface that a {@code java:} import
 * may reach (I082-G, PLAT052).
 *
 * <p>A Java class being loadable or present on the classpath never makes it importable: {@code
 * import("java:name")} resolves only against the exact {@link Class} values admitted here, and only
 * the constructor and methods explicitly exposed for each cross the provider boundary. Nothing is
 * looked up by name, scanned, loaded, or initialized on behalf of guest code; the embedder supplies
 * the already-provisioned classes, and member selection happens once, here, from embedder code.
 *
 * <p>The catalogue is the confinement of the restricted host-Java provider. By exposing a member
 * the embedder attests that invoking it amplifies no authority: no authority is provisioned to the
 * provider, so an exposed member must not reach I/O, network, processes, native code, environment,
 * class loading, or reflection. The catalogue itself keeps every exposed surface non-amplifying at
 * the boundary:
 *
 * <ul>
 *   <li>only public members declared by the admitted class itself, never inherited ones such as
 *       {@code getClass};
 *   <li>at most one constructor per class and one method per selector and kind, so no Protos call
 *       ever selects among Java overloads;
 *   <li>parameter and result types limited to Java scalars with a lossless D188 mapping and to
 *       admitted classes, so no {@code Class}, loader, reflection, or other unadmitted host object
 *       can be returned to or requested from guest code.
 * </ul>
 */
public final class ProtosHostJavaCatalogue {
    /** Java types with a lossless D188 scalar mapping in both directions. */
    private static final Set<Class<?>> SCALAR_TYPES =
            Set.of(
                    boolean.class, Boolean.class,
                    byte.class, Byte.class,
                    short.class, Short.class,
                    int.class, Integer.class,
                    long.class, Long.class,
                    BigInteger.class,
                    float.class, Float.class,
                    double.class, Double.class,
                    String.class);

    private final Map<String, Entry> byName;
    private final Map<Class<?>, Entry> byClass;

    private ProtosHostJavaCatalogue(Map<String, Entry> byName) {
        this.byName = Map.copyOf(byName);
        IdentityHashMap<Class<?>, Entry> classes = new IdentityHashMap<>();
        for (Entry entry : byName.values()) {
            classes.put(entry.type(), entry);
        }
        this.byClass = classes;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The admitted entry of the exact Java binary class name; a pure lookup with no loading. */
    Optional<Entry> lookup(String className) {
        return Optional.ofNullable(byName.get(className));
    }

    /** The admitted entry of exactly {@code type}, never of a subclass or superclass. */
    Optional<Entry> lookup(Class<?> type) {
        return Optional.ofNullable(byClass.get(type));
    }

    boolean isEmpty() {
        return byName.isEmpty();
    }

    static boolean isScalarType(Class<?> type) {
        return SCALAR_TYPES.contains(type);
    }

    /**
     * One admitted class with its exposed members. It is immutable provider metadata, never a
     * guest value; the facade of a {@code java:} import privately targets it, and its exposed
     * constructor is the facade's {@code call}.
     */
    record Entry(
            Class<?> type,
            Constructor<?> constructor,
            Map<String, Method> staticMethods,
            Map<String, Method> instanceMethods) {
        Entry {
            Objects.requireNonNull(type, "type");
            staticMethods = Map.copyOf(staticMethods);
            instanceMethods = Map.copyOf(instanceMethods);
        }
    }

    /** Embedder-side builder; every exposure is checked when it is declared and at build time. */
    public static final class Builder {
        private final Map<String, Draft> drafts = new LinkedHashMap<>();

        private Builder() {}

        /** Exposes the exact public constructor of {@code type}; the only one of that class. */
        public Builder exposeConstructor(Class<?> type, Class<?>... parameterTypes)
                throws NoSuchMethodException {
            Draft draft = draft(type);
            if (draft.constructor != null) {
                throw new IllegalArgumentException(
                        "Java class already exposes a constructor: " + type.getName());
            }
            if (Modifier.isAbstract(type.getModifiers())) {
                throw new IllegalArgumentException(
                        "an abstract Java class cannot be constructed: " + type.getName());
            }
            draft.constructor = type.getConstructor(parameterTypes.clone());
            return this;
        }

        /** Exposes one exact public static method declared by {@code type} under its name. */
        public Builder exposeStaticMethod(Class<?> type, String name, Class<?>... parameterTypes)
                throws NoSuchMethodException {
            Draft draft = draft(type);
            put(draft.staticMethods, method(type, name, parameterTypes, true));
            return this;
        }

        /** Exposes one exact public instance method declared by {@code type} under its name. */
        public Builder exposeInstanceMethod(
                Class<?> type, String name, Class<?>... parameterTypes)
                throws NoSuchMethodException {
            Draft draft = draft(type);
            put(draft.instanceMethods, method(type, name, parameterTypes, false));
            return this;
        }

        /** Validates every exposed signature against the final set of admitted classes. */
        public ProtosHostJavaCatalogue build() {
            Map<String, Entry> entries = new LinkedHashMap<>();
            IdentityHashMap<Class<?>, Boolean> admitted = new IdentityHashMap<>();
            for (Draft draft : drafts.values()) {
                admitted.put(draft.type, Boolean.TRUE);
            }
            for (Draft draft : drafts.values()) {
                if (draft.constructor != null) {
                    requireBoundaryTypes(draft.constructor, void.class, admitted);
                }
                for (Method method : draft.staticMethods.values()) {
                    requireBoundaryTypes(method, method.getReturnType(), admitted);
                }
                for (Method method : draft.instanceMethods.values()) {
                    requireBoundaryTypes(method, method.getReturnType(), admitted);
                }
                entries.put(
                        draft.type.getName(),
                        new Entry(
                                draft.type,
                                draft.constructor,
                                draft.staticMethods,
                                draft.instanceMethods));
            }
            return new ProtosHostJavaCatalogue(entries);
        }

        private Draft draft(Class<?> type) {
            Objects.requireNonNull(type, "type");
            if (type.isPrimitive()
                    || type.isArray()
                    || type.isInterface()
                    || !Modifier.isPublic(type.getModifiers())) {
                throw new IllegalArgumentException(
                        "only public Java classes can be admitted: " + type.getName());
            }
            Draft draft = drafts.computeIfAbsent(type.getName(), ignored -> new Draft(type));
            if (draft.type != type) {
                throw new IllegalArgumentException(
                        "two admitted Java classes share one name: " + type.getName());
            }
            return draft;
        }

        private static Method method(
                Class<?> type, String name, Class<?>[] parameterTypes, boolean isStatic)
                throws NoSuchMethodException {
            Method method =
                    type.getMethod(Objects.requireNonNull(name, "name"), parameterTypes.clone());
            if (method.getDeclaringClass() != type) {
                throw new IllegalArgumentException(
                        "only methods declared by the admitted class can be exposed: " + name);
            }
            if (Modifier.isStatic(method.getModifiers()) != isStatic) {
                throw new IllegalArgumentException(
                        "Java method " + name + (isStatic ? " is not static" : " is static"));
            }
            return method;
        }

        /** One method per selector: the catalogue never encodes a Java overload choice. */
        private static void put(Map<String, Method> methods, Method method) {
            if (methods.putIfAbsent(method.getName(), method) != null) {
                throw new IllegalArgumentException(
                        "Java selector is already exposed: " + method.getName());
            }
        }

        private static void requireBoundaryTypes(
                Executable executable,
                Class<?> resultType,
                IdentityHashMap<Class<?>, Boolean> admitted) {
            for (Class<?> parameter : executable.getParameterTypes()) {
                if (!isScalarType(parameter) && !admitted.containsKey(parameter)) {
                    throw new IllegalArgumentException(
                            "unsupported Java parameter type " + parameter.getName()
                                    + " of " + executable.getName());
                }
            }
            if (resultType != void.class
                    && !isScalarType(resultType)
                    && !admitted.containsKey(resultType)) {
                throw new IllegalArgumentException(
                        "unsupported Java result type " + resultType.getName()
                                + " of " + executable.getName());
            }
        }
    }

    private static final class Draft {
        final Class<?> type;
        Constructor<?> constructor;
        final Map<String, Method> staticMethods = new LinkedHashMap<>();
        final Map<String, Method> instanceMethods = new LinkedHashMap<>();

        Draft(Class<?> type) {
            this.type = type;
        }
    }
}
