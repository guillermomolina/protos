/* APL-1.0 licensed work; see LICENSE.TXT. */
package com.guillermomolina.protos.runtime;
import java.util.*;
public final class ProtosPathValue implements ProtosRepresentedValue {
 public sealed interface Component permits Normal,Parent {}
 public record Normal(String name) implements Component { public Normal{Objects.requireNonNull(name,"name");} }
 public enum Parent implements Component { INSTANCE }
 private final ProtosObjectValue prototype; private final boolean rooted; private final List<Component> components;
 public ProtosPathValue(ProtosObjectValue p,boolean rooted,List<Component> c){this.prototype=Objects.requireNonNull(p);this.rooted=rooted;this.components=List.copyOf(c);}
 public boolean rooted(){return rooted;} public List<Component> components(){return components;}
 public ProtosPathValue child(String n){var x=new ArrayList<Component>(components);x.add(new Normal(n));return new ProtosPathValue(prototype,rooted,x);}
 public ProtosPathValue parentComponent(){var x=new ArrayList<Component>(components);x.add(Parent.INSTANCE);return new ProtosPathValue(prototype,rooted,x);}
 public boolean structurallyEquals(ProtosPathValue o){return o!=null&&rooted==o.rooted&&components.equals(o.components);}
 public int structuralHash(){return 31*Boolean.hashCode(rooted)+components.hashCode();}
 @Override public Object representedDelegationParent(ProtosPrelude p){return prototype;}
}
