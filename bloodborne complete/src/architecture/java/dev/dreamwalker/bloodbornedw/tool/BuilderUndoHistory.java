package dev.dreamwalker.bloodbornedw.tool;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Bounded session journal; callers own authority, conflict checks and restoration. */
final class BuilderUndoHistory<K,S> {
    static final int LIMIT=20;
    record Entry<K,S>(K key,String operation,S before,S after) {}
    private final Deque<Entry<K,S>> entries=new ArrayDeque<>();
    void record(K key,String operation,S before,S after){
        if(before==null||after==null||Objects.equals(before,after))return;
        if(entries.size()==LIMIT)entries.removeFirst();
        entries.addLast(new Entry<>(key,operation,before,after));
    }
    Entry<K,S> latest(){return entries.peekLast();}
    void removeLatest(){entries.removeLast();}
    void invalidate(K key){entries.removeIf(entry->entry.key().equals(key));}
    void invalidate(K key,java.util.function.Predicate<Entry<K,S>> affected){entries.removeIf(entry->entry.key().equals(key)&&affected.test(entry));}
    void clear(){entries.clear();}
    int size(){return entries.size();}
}
