package dev.dreamwalker.bloodborneblocks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.state.property.Property;

/** Finite authored visual alternatives; serialized names are part of the contract. */
final class LogicalVariantProperty extends Property<String> {
 private final List<String> values;
 LogicalVariantProperty(String name,List<String> values) {
  super(name, String.class);
  if(values==null||values.isEmpty()||values.size()>64||values.stream().distinct().count()!=values.size()
    ||values.stream().anyMatch(v->v==null||!v.matches("[a-z0-9_]+")))throw new IllegalArgumentException("Invalid logical variants");
  this.values=List.copyOf(values);
 }
 public Collection<String> getValues(){return values;}
 public Optional<String> parse(String value){return values.contains(value)?Optional.of(value):Optional.empty();}
 public String name(String value){return value;}
 @Override public boolean equals(Object other){return this==other||other instanceof LogicalVariantProperty p&&super.equals(other)&&values.equals(p.values);}
 @Override public int computeHashCode(){return 31*super.computeHashCode()+values.hashCode();}
}
