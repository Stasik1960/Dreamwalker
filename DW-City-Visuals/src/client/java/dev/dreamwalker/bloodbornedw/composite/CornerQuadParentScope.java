package dev.dreamwalker.bloodbornedw.composite;
/** Thread-confined parent identity during one child emission. No reference survives the call. */
public final class CornerQuadParentScope {
 private record Node(Object child,Object parent,Node previous){}
 private static final ThreadLocal<Node> CURRENT=new ThreadLocal<>();
 private CornerQuadParentScope(){}
 public static Object parentFor(Object child){for(Node node=CURRENT.get();node!=null;node=node.previous())if(node.child()==child)return node.parent();return null;}
 /** Associate a temporary copy with the same parent until the enclosing emission ends. */
 public static void alias(Object child,Object copy){Object parent=parentFor(child);if(parent==null)return;if(copy==null||copy==parent||copy==child)throw new IllegalArgumentException("Distinct temporary copy required");CURRENT.set(new Node(copy,parent,CURRENT.get()));}
 public static void emit(Object child,Object parent,Runnable action){
  if(child==null||parent==null||child==parent)throw new IllegalArgumentException("Distinct child and parent required");
  Node previous=CURRENT.get();CURRENT.set(new Node(child,parent,previous));
  try{action.run();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
 }
}
