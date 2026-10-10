package dev.dreamwalker.bloodbornedw.tool;

/** Pure session-journal checks; requires no Minecraft world or server. */
public final class BuilderUndoChecks {
    public static void main(String[] args){
        var journal=new BuilderUndoHistory<String,Integer>();
        for(int i=0;i<25;i++)journal.record("a","offset",i,i+1);
        require(journal.size()==20,"bounded successful history");
        journal.record("a","offset",25,25);
        journal.record("a","offset",null,26);
        require(journal.size()==20&&journal.latest().after()==25,"no-op and missing snapshots omitted");
        for(int i=24;i>=5;i--){require(journal.latest().before()==i,"LIFO retains latest twenty");journal.removeLatest();}
        require(journal.latest()==null,"history exhausted");
        journal.record("a","geometry",1,2);journal.record("b","dogs",3,4);journal.record("a","geometry",2,3);
        journal.invalidate("a");
        require(journal.size()==1&&journal.latest().key().equals("b"),"foreign edit invalidates all older same-object edits including ABA");
        journal.clear();require(journal.size()==0,"session cleanup");
        journal.record("a","offset",1,2);journal.record("a","dogs",2,3);journal.record("b","offset",3,4);
        journal.invalidate("a",entry->entry.operation().equals("offset"));
        require(journal.size()==2&&journal.latest().key().equals("b"),"unrelated fields and other instances survive conflicts");
        journal.removeLatest();require(journal.latest().operation().equals("dogs"),"same-object unrelated edit remains undoable");
        var otherPlayer=new BuilderUndoHistory<String,Integer>();otherPlayer.record("a","offset",8,9);
        journal.clear();require(otherPlayer.size()==1,"connection histories are independent");
        System.out.println("BuilderUndoChecks: passed");
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
