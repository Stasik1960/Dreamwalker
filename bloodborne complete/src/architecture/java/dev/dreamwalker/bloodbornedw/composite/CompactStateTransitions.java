package dev.dreamwalker.bloodbornedw.composite;

import com.google.common.collect.ArrayTable;
import com.google.common.collect.Table;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.function.BiFunction;
import net.minecraft.state.property.Property;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import dev.dreamwalker.bloodbornedw.composite.mixin.StateTransitionAccess;

/** Same Cartesian Table contract as the original ArrayTable, with shared
 * domains and only each property's own value slots in every state. A normally
 * null foreign-domain cell can still be put by an external caller: its rare
 * overflow map is allocated only on that mutation. No world/cell keys exist. */
public final class CompactStateTransitions<V> implements Table<Property<?>,Comparable<?>,V> {
    private static final int MAX_LAYOUTS=4096,MAX_COLUMNS_PER_LAYOUT=4096;
    private static final Map<List<Property<?>>,Rows> ROWS=new ConcurrentHashMap<>();
    private static final AtomicLong CONVERTED=new AtomicLong(),OLD_SLOTS=new AtomicLong(),NEW_SLOTS=new AtomicLong(),FALLBACK=new AtomicLong(),INELIGIBLE=new AtomicLong();
    private static final class UnsupportedLayout extends RuntimeException {}
    private static final class Rows {
        final List<Property<?>> keys;final Set<Property<?>> keySet;final Map<Property<?>,Integer> index;
        final List<Map<Object,Integer>> values;final int[] offsets;final int slots;
        final Map<List<Comparable<?>>,Columns> columns=new ConcurrentHashMap<>();
        Rows(List<Property<?>> supplied){
            if(supplied.isEmpty()||supplied.size()>16)throw new UnsupportedLayout();
            keys=List.copyOf(supplied);keySet=Collections.unmodifiableSet(new LinkedHashSet<>(keys));
            Map<Property<?>,Integer> indexes=new HashMap<>();List<Map<Object,Integer>> domains=new ArrayList<>();offsets=new int[keys.size()];int count=0;
            for(int r=0;r<keys.size();r++){
                var property=keys.get(r);if(indexes.put(property,r)!=null)throw new UnsupportedLayout();offsets[r]=count;
                Map<Object,Integer> domain=new HashMap<>();for(var value:property.getValues())if(domain.put(value,count++)!=null)throw new UnsupportedLayout();
                if(domain.isEmpty()||domain.size()>64)throw new UnsupportedLayout();domains.add(Map.copyOf(domain));
            }
            if(count>256)throw new UnsupportedLayout();slots=count;index=Map.copyOf(indexes);values=List.copyOf(domains);
        }
        Columns columns(List<Comparable<?>> supplied){
            var found=columns.get(supplied);if(found!=null)return found;
            if(columns.size()>=MAX_COLUMNS_PER_LAYOUT)throw new UnsupportedLayout();
            var next=new Columns(supplied,this);var raced=columns.putIfAbsent(next.keys,next);return raced==null?next:raced;
        }
    }
    private static final class Columns {
        final List<Comparable<?>> keys;final Set<Comparable<?>> keySet;final Map<Comparable<?>,Integer> index;
        Columns(List<Comparable<?>> supplied,Rows rows){
            if(supplied.isEmpty()||supplied.size()>64)throw new UnsupportedLayout();keys=List.copyOf(supplied);
            Map<Comparable<?>,Integer> indexes=new HashMap<>();
            for(int c=0;c<keys.size();c++){
                var value=keys.get(c);if(indexes.put(value,c)!=null||rows.values.stream().noneMatch(domain->domain.containsKey(value)))throw new UnsupportedLayout();
            }
            index=Map.copyOf(indexes);keySet=Collections.unmodifiableSet(new LinkedHashSet<>(keys));
        }
    }
    private final Rows rows;private final Columns columns;private final Object[] targets;
    private Map<Integer,V> overflow;
    private CompactStateTransitions(ArrayTable<Property<?>,Comparable<?>,V> old){
        var rowKeys=old.rowKeyList();var found=ROWS.get(rowKeys);
        if(found==null){if(ROWS.size()>=MAX_LAYOUTS)throw new UnsupportedLayout();var next=new Rows(rowKeys);var raced=ROWS.putIfAbsent(next.keys,next);found=raced==null?next:raced;}
        rows=found;columns=rows.columns(old.columnKeyList());targets=new Object[rows.slots];
        // Check every old Cartesian cell before the old table is discarded.
        // Foreign-domain non-null values are not native State initializer data.
        for(int r=0;r<rows.keys.size();r++)for(int c=0;c<columns.keys.size();c++){
            V value=old.at(r,c);Integer slot=rows.values.get(r).get(columns.keys.get(c));
            if(slot!=null)targets[slot]=value;else if(value!=null)throw new UnsupportedLayout();
        }
        for(int r=0;r<rows.keys.size();r++)for(int c=0;c<columns.keys.size();c++)
            if(old.at(r,c)!=at(r,c))throw new IllegalStateException("NEW native state transition copy changed a neighbor/null cell");
        if(old.size()!=size()||!old.rowKeyList().equals(rows.keys)||!old.columnKeyList().equals(columns.keys))throw new IllegalStateException("NEW native state transition domain changed");
    }
    @SuppressWarnings("unchecked")
    public static <V> Table<Property<?>,Comparable<?>,V> compact(Table<Property<?>,Comparable<?>,V> old){
        if(!(old instanceof ArrayTable<?,?,?>)){FALLBACK.incrementAndGet();return old;}
        try{
            var next=new CompactStateTransitions<>((ArrayTable<Property<?>,Comparable<?>,V>)old);
            CONVERTED.incrementAndGet();OLD_SLOTS.addAndGet(old.size());NEW_SLOTS.addAndGet(next.targets.length);return next;
        }catch(UnsupportedLayout unknown){FALLBACK.incrementAndGet();return old;}
    }
    public static boolean ownedClass(Object owner){if(!(owner instanceof Block))return false;String name=owner.getClass().getName();return name.startsWith("dev.dreamwalker.bloodbornedw.")||name.startsWith("dev.dreamwalker.bloodbornerp.");}
    /** Covers our rare plain vanilla Block instances after their namespace is
     * actually known. Vanilla and foreign registrations cannot enter this pass. */
    @SuppressWarnings({"unchecked","rawtypes"})public static void optimizeRegistered(){
        for(var id:Registries.BLOCK.getIds())if(id.getNamespace().equals("bloodborne_dw")||id.getNamespace().equals("bloodborne_rp"))for(var state:Registries.BLOCK.get(id).getStateManager().getStates()){
            var access=(StateTransitionAccess)state;var old=access.bloodborne_dw$transitions();
            if(old instanceof CompactStateTransitions<?>||old==null||old.isEmpty())continue;
            access.bloodborne_dw$transitions(compact((Table)old));
        }
    }
    public static void ineligible(){INELIGIBLE.incrementAndGet();}
    public static Map<String,Object> metrics(){Map<String,Object> values=new LinkedHashMap<>();values.put("convertedOwnModStateTables",CONVERTED.get());values.put("oldCartesianReferenceSlots",OLD_SLOTS.get());values.put("compactPropertyReferenceSlots",NEW_SLOTS.get());values.put("guardedFallbackTables",FALLBACK.get());values.put("initiallyIneligibleClassTables",INELIGIBLE.get());values.put("sharedRowLayouts",ROWS.size());values.put("sharedColumnLayouts",ROWS.values().stream().mapToInt(row->row.columns.size()).sum());values.put("originalV12MemoryOptimizationAuthorized",true);values.put("metricScope","Actual own-mod table/reference-slot counts, not total JVM heap or mod byte attribution");return Map.copyOf(values);}
    @SuppressWarnings("unchecked")private V at(int r,int c){Integer slot=rows.values.get(r).get(columns.keys.get(c));return slot!=null?(V)targets[slot]:overflow==null?null:overflow.get(r*columns.keys.size()+c);}
    private V set(int r,int c,V value){
        V before=at(r,c);Integer slot=rows.values.get(r).get(columns.keys.get(c));
        if(slot!=null)targets[slot]=value;
        else if(value!=null){if(overflow==null)overflow=new HashMap<>();overflow.put(r*columns.keys.size()+c,value);}
        else if(overflow!=null){overflow.remove(r*columns.keys.size()+c);if(overflow.isEmpty())overflow=null;}
        return before;
    }
    @SuppressWarnings("unchecked")@Override public V get(Object row,Object column){
        if(row==null||column==null)return null;
        Integer r=rows.index.get(row);if(r==null)return null;Integer slot=rows.values.get(r).get(column);
        if(slot!=null)return (V)targets[slot];if(overflow==null)return null;Integer c=columns.index.get(column);return c==null?null:overflow.get(r*columns.keys.size()+c);
    }
    @Override public boolean contains(Object row,Object column){return row!=null&&column!=null&&rows.index.containsKey(row)&&columns.index.containsKey(column);}
    @Override public boolean containsRow(Object row){return row!=null&&rows.index.containsKey(row);}
    @Override public boolean containsColumn(Object column){return column!=null&&columns.index.containsKey(column);}
    @Override public boolean containsValue(Object value){for(int r=0;r<rows.keys.size();r++)for(int c=0;c<columns.keys.size();c++)if(Objects.equals(value,at(r,c)))return true;return false;}
    @Override public boolean isEmpty(){return rows.keys.isEmpty();}
    @Override public int size(){return rows.keys.size()*columns.keys.size();}
    @Override public Set<Property<?>> rowKeySet(){return rows.keySet;}
    @Override public Set<Comparable<?>> columnKeySet(){return columns.keySet;}
    @Override public V put(Property<?> row,Comparable<?> column,V value){
        Objects.requireNonNull(row);Objects.requireNonNull(column);Integer r=rows.index.get(row),c=columns.index.get(column);
        if(r==null||c==null)throw new IllegalArgumentException("State table row/column outside original domains");return set(r,c,value);
    }
    @Override public void putAll(Table<? extends Property<?>,? extends Comparable<?>,? extends V> table){for(var cell:table.cellSet())put(cell.getRowKey(),cell.getColumnKey(),cell.getValue());}
    @Override public V remove(Object row,Object column){throw new UnsupportedOperationException();}
    @Override public void clear(){throw new UnsupportedOperationException();}
    private static <K,T> Map<K,T> view(List<K> keys,Map<K,Integer> index,IntFunction<T> getter,BiFunction<Integer,T,T> setter){
        return new AbstractMap<>(){
            @Override public int size(){return keys.size();}
            @Override public Set<K> keySet(){return Collections.unmodifiableSet(new LinkedHashSet<>(keys));}
            @Override public boolean containsKey(Object key){return key!=null&&index.containsKey(key);}
            @Override public T get(Object key){Integer i=key==null?null:index.get(key);return i==null?null:getter.apply(i);}
            @Override public T put(K key,T value){Integer i=key==null?null:index.get(key);if(i==null)throw new IllegalArgumentException("State table key outside original domain");if(setter==null)throw new UnsupportedOperationException();return setter.apply(i,value);}
            @Override public T remove(Object key){throw new UnsupportedOperationException();}
            @Override public void clear(){throw new UnsupportedOperationException();}
            @Override public Set<Entry<K,T>> entrySet(){return new AbstractSet<>(){
                @Override public int size(){return keys.size();}
                @Override public Iterator<Entry<K,T>> iterator(){return new Iterator<>(){int i;
                    @Override public boolean hasNext(){return i<keys.size();}
                    @Override public Entry<K,T> next(){if(!hasNext())throw new NoSuchElementException();final int at=i++;return new Entry<>(){
                        @Override public K getKey(){return keys.get(at);}
                        @Override public T getValue(){return getter.apply(at);}
                        @Override public T setValue(T value){if(setter==null)throw new UnsupportedOperationException();return setter.apply(at,value);}
                        @Override public boolean equals(Object other){return other instanceof Entry<?,?> entry&&Objects.equals(getKey(),entry.getKey())&&Objects.equals(getValue(),entry.getValue());}
                        @Override public int hashCode(){return Objects.hashCode(getKey())^Objects.hashCode(getValue());}
                        @Override public String toString(){return getKey()+"="+getValue();}
                    };}
                    @Override public void remove(){throw new UnsupportedOperationException();}
                };}
            };}
        };
    }
    @Override public Map<Comparable<?>,V> row(Property<?> row){Objects.requireNonNull(row);Integer r=rows.index.get(row);return r==null?Collections.emptyMap():view(columns.keys,columns.index,c->at(r,c),(c,value)->set(r,c,value));}
    @Override public Map<Property<?>,V> column(Comparable<?> column){Objects.requireNonNull(column);Integer c=columns.index.get(column);return c==null?Collections.emptyMap():view(rows.keys,rows.index,r->at(r,c),(r,value)->set(r,c,value));}
    @Override public Map<Property<?>,Map<Comparable<?>,V>> rowMap(){return view(rows.keys,rows.index,r->row(rows.keys.get(r)),null);}
    @Override public Map<Comparable<?>,Map<Property<?>,V>> columnMap(){return view(columns.keys,columns.index,c->column(columns.keys.get(c)),null);}
    @Override public Set<Cell<Property<?>,Comparable<?>,V>> cellSet(){return new AbstractSet<>(){
        @Override public int size(){return CompactStateTransitions.this.size();}
        @Override public boolean contains(Object value){return value instanceof Table.Cell<?,?,?> cell&&CompactStateTransitions.this.contains(cell.getRowKey(),cell.getColumnKey())&&Objects.equals(get(cell.getRowKey(),cell.getColumnKey()),cell.getValue());}
        @Override public Iterator<Cell<Property<?>,Comparable<?>,V>> iterator(){return new Iterator<>(){int i;
            @Override public boolean hasNext(){return i<size();}
            @Override public Cell<Property<?>,Comparable<?>,V> next(){if(!hasNext())throw new NoSuchElementException();final int r=i/columns.keys.size(),c=i++%columns.keys.size();return new Cell<>(){
                @Override public Property<?> getRowKey(){return rows.keys.get(r);}
                @Override public Comparable<?> getColumnKey(){return columns.keys.get(c);}
                @Override public V getValue(){return at(r,c);}
                @Override public boolean equals(Object other){return other instanceof Table.Cell<?,?,?> cell&&Objects.equals(getRowKey(),cell.getRowKey())&&Objects.equals(getColumnKey(),cell.getColumnKey())&&Objects.equals(getValue(),cell.getValue());}
                @Override public int hashCode(){return Objects.hash(getRowKey(),getColumnKey(),getValue());}
                @Override public String toString(){return "("+getRowKey()+","+getColumnKey()+")="+getValue();}
            };}
            @Override public void remove(){throw new UnsupportedOperationException();}
        };}
    };}
    @Override public Collection<V> values(){return new AbstractCollection<>(){
        @Override public int size(){return CompactStateTransitions.this.size();}
        @Override public Iterator<V> iterator(){var cells=cellSet().iterator();return new Iterator<>(){
            @Override public boolean hasNext(){return cells.hasNext();}
            @Override public V next(){return cells.next().getValue();}
            @Override public void remove(){throw new UnsupportedOperationException();}
        };}
    };}
    @Override public boolean equals(Object other){return other==this||other instanceof Table<?,?,?> table&&cellSet().equals(table.cellSet());}
    @Override public int hashCode(){return cellSet().hashCode();}
    @Override public String toString(){return rowMap().toString();}
}
