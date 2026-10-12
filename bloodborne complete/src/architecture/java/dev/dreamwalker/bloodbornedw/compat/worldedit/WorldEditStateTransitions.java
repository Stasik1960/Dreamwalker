package dev.dreamwalker.bloodbornedw.compat.worldedit;

import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import java.util.*;

/** Optional WorldEdit state-cache representation only. Its public states, native
 * IDs, property ranges and complete Cartesian state map remain untouched.
 * Instead of one materialized neighbour graph per state, each type shares one
 * mixed-radix array; a transition changes only the selected property digit.
 * No world/position/owner data is cached here. Foreign namespaces use WE's
 * original ImmutableTable implementation. */
public final class WorldEditStateTransitions implements Table<Property<?>,Object,BlockState> {
    private static final Map<BlockType,Layout> LAYOUTS=new HashMap<>();
    private static long converted,avoidedCells;
    private static final class Layout {
        final Map<Map<Property<?>,Object>,BlockState> identity;
        final List<Property<?>> properties;
        final Map<Property<?>,Integer> rows;
        final List<List<?>> domains;
        final List<Map<Object,Integer>> digits;
        final int[] strides;
        final BlockState[] states;
        final int neighborSize;
        Layout(BlockType type,Map<Map<Property<?>,Object>,BlockState> map){
            identity=map;properties=List.copyOf(type.getProperties());
            Map<Property<?>,Integer> indexes=new HashMap<>();List<List<?>> values=new ArrayList<>();List<Map<Object,Integer>> valueIndexes=new ArrayList<>();strides=new int[properties.size()];int product=1;
            for(int i=properties.size()-1;i>=0;i--){Property<?> property=properties.get(i);indexes.put(property,i);strides[i]=product;product=Math.multiplyExact(product,property.getValues().size());}
            if(product!=map.size())throw new IllegalArgumentException("Incomplete WorldEdit Cartesian state map for "+type);
            rows=Map.copyOf(indexes);
            for(Property<?> property:properties){List<?> domain=List.copyOf(property.getValues());values.add(domain);Map<Object,Integer> lookup=new HashMap<>();for(int i=0;i<domain.size();i++)lookup.put(domain.get(i),i);valueIndexes.add(Map.copyOf(lookup));}
            domains=List.copyOf(values);digits=List.copyOf(valueIndexes);states=new BlockState[product];neighborSize=domains.stream().mapToInt(v->v.size()-1).sum();
            for(var entry:map.entrySet()){int index=index(entry.getKey());if(index<0||states[index]!=null)throw new IllegalArgumentException("Ambiguous WorldEdit state layout for "+type);states[index]=entry.getValue();}
            for(BlockState state:states)if(state==null)throw new IllegalArgumentException("Missing WorldEdit state for "+type);
        }
        int index(Map<Property<?>,Object> values){if(values.size()!=properties.size())return -1;int result=0;for(int r=0;r<properties.size();r++){Integer d=digits.get(r).get(values.get(properties.get(r)));if(d==null)return -1;result+=d*strides[r];}return result;}
        int digit(int state,int row){return (state/strides[row])%domains.get(row).size();}
    }
    private final Layout layout;
    private final int index;
    private WorldEditStateTransitions(Layout layout,int index){this.layout=layout;this.index=index;}
    public static boolean own(BlockType type){String id=type.getId();return id.startsWith("bloodborne_dw:")||id.startsWith("bloodborne_rp:");}
    public static synchronized Table<Property<?>,Object,BlockState> create(BlockType type,Map<Property<?>,Object> values,Map<Map<Property<?>,Object>,BlockState> map){
        Layout layout=LAYOUTS.get(type);if(layout==null||layout.identity!=map){layout=new Layout(type,map);LAYOUTS.put(type,layout);}
        int index=layout.index(values);if(index<0)throw new IllegalArgumentException("Invalid WorldEdit source state");
        converted++;avoidedCells+=layout.neighborSize;
        return new WorldEditStateTransitions(layout,index);
    }
    public static synchronized Map<String,Long> statistics(){return Map.of("types",(long)LAYOUTS.size(),"states",converted,"avoidedNeighborCells",avoidedCells);}
    @Override public BlockState get(Object row,Object value){
        if(row==null||value==null)return null;
        Integer r=layout.rows.get(row);if(r==null)return null;Integer d=layout.digits.get(r).get(value);if(d==null)return null;
        int current=layout.digit(index,r);if(d==current)return null;return layout.states[index+(d-current)*layout.strides[r]];
    }
    @Override public int size(){return layout.neighborSize;}
    @Override public boolean isEmpty(){return size()==0;}
    @Override public boolean contains(Object row,Object column){return get(row,column)!=null;}
    @Override public boolean containsRow(Object row){return row!=null&&layout.rows.containsKey(row)&&layout.domains.get(layout.rows.get(row)).size()>1;}
    @Override public boolean containsColumn(Object column){return column!=null&&!column(column).isEmpty();}
    @Override public boolean containsValue(Object value){if(value==null)return false;for(var cell:cellSet())if(Objects.equals(value,cell.getValue()))return true;return false;}
    private Iterator<Cell<Property<?>,Object,BlockState>> cellIterator(){
        return new Iterator<>(){int row,digit;Cell<Property<?>,Object,BlockState> next;
            private void advance(){while(next==null&&row<layout.properties.size()){if(digit>=layout.domains.get(row).size()){row++;digit=0;continue;}Object value=layout.domains.get(row).get(digit++);BlockState state=get(layout.properties.get(row),value);if(state!=null)next=Tables.immutableCell(layout.properties.get(row),value,state);}}
            @Override public boolean hasNext(){advance();return next!=null;}
            @Override public Cell<Property<?>,Object,BlockState> next(){advance();if(next==null)throw new NoSuchElementException();var result=next;next=null;return result;}
            @Override public void remove(){throw new UnsupportedOperationException("Immutable state transitions");}
        };
    }
    @Override public Set<Cell<Property<?>,Object,BlockState>> cellSet(){return Collections.unmodifiableSet(new AbstractSet<>(){
        @Override public int size(){return WorldEditStateTransitions.this.size();}
        @Override public Iterator<Cell<Property<?>,Object,BlockState>> iterator(){return cellIterator();}
        @Override public boolean contains(Object candidate){return candidate instanceof Table.Cell<?,?,?> cell&&WorldEditStateTransitions.this.contains(cell.getRowKey(),cell.getColumnKey())&&Objects.equals(get(cell.getRowKey(),cell.getColumnKey()),cell.getValue());}
    });}
    @Override public Collection<BlockState> values(){return Collections.unmodifiableCollection(new AbstractCollection<>(){
        @Override public int size(){return WorldEditStateTransitions.this.size();}
        @Override public Iterator<BlockState> iterator(){var cells=cellIterator();return new Iterator<>(){public boolean hasNext(){return cells.hasNext();}public BlockState next(){return cells.next().getValue();}};}
    });}
    @Override public Set<Property<?>> rowKeySet(){return rowMap().keySet();}
    @Override public Set<Object> columnKeySet(){return columnMap().keySet();}
    @Override public Map<Object,BlockState> row(Property<?> property){Objects.requireNonNull(property);Integer r=layout.rows.get(property);if(r==null)return Map.of();Map<Object,BlockState> result=new LinkedHashMap<>();for(Object value:layout.domains.get(r)){BlockState state=get(property,value);if(state!=null)result.put(value,state);}return Collections.unmodifiableMap(result);}
    @Override public Map<Property<?>,BlockState> column(Object value){Objects.requireNonNull(value);Map<Property<?>,BlockState> result=new LinkedHashMap<>();for(Property<?> property:layout.properties){BlockState state=get(property,value);if(state!=null)result.put(property,state);}return Collections.unmodifiableMap(result);}
    @Override public Map<Property<?>,Map<Object,BlockState>> rowMap(){Map<Property<?>,Map<Object,BlockState>> result=new LinkedHashMap<>();for(Property<?> property:layout.properties){var row=row(property);if(!row.isEmpty())result.put(property,row);}return Collections.unmodifiableMap(result);}
    @Override public Map<Object,Map<Property<?>,BlockState>> columnMap(){Map<Object,Map<Property<?>,BlockState>> result=new LinkedHashMap<>();for(var cell:cellSet())result.computeIfAbsent(cell.getColumnKey(),this::column);return Collections.unmodifiableMap(result);}
    @Override public BlockState put(Property<?> row,Object column,BlockState value){throw new UnsupportedOperationException("Immutable state transitions");}
    @Override public void putAll(Table<? extends Property<?>,? extends Object,? extends BlockState> table){throw new UnsupportedOperationException("Immutable state transitions");}
    @Override public BlockState remove(Object row,Object column){throw new UnsupportedOperationException("Immutable state transitions");}
    @Override public void clear(){throw new UnsupportedOperationException("Immutable state transitions");}
    @Override public boolean equals(Object other){return other==this||other instanceof Table<?,?,?> table&&cellSet().equals(table.cellSet());}
    @Override public int hashCode(){return cellSet().hashCode();}
    @Override public String toString(){return rowMap().toString();}
}
