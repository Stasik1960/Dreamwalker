package dev.dreamwalker.bloodbornedw.composite;
public final class CornerQuadParentScopeTest {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Object parent=new Object(),child=new Object(),nestedParent=child,nestedChild=new Object();
  check(CornerQuadParentScope.parentFor(child)==null,"initial");
  CornerQuadParentScope.emit(child,parent,()->{
   check(CornerQuadParentScope.parentFor(child)==parent,"parent scope");
   check(CornerQuadParentScope.parentFor(parent)==null,"parent is not child");
   java.util.concurrent.atomic.AtomicBoolean isolated=new java.util.concurrent.atomic.AtomicBoolean();
   Thread thread=new Thread(()->isolated.set(CornerQuadParentScope.parentFor(child)==null));thread.start();try{thread.join();}catch(InterruptedException e){throw new AssertionError(e);}check(isolated.get(),"thread isolation");
   try{CornerQuadParentScope.emit(nestedChild,nestedParent,()->{check(CornerQuadParentScope.parentFor(nestedChild)==nestedParent,"nested scope");check(CornerQuadParentScope.parentFor(nestedParent)==parent,"nested chain parent lookup");throw new IllegalStateException("expected");});throw new AssertionError("missing exception");}catch(IllegalStateException expected){}
   check(CornerQuadParentScope.parentFor(child)==parent,"nested restoration");check(CornerQuadParentScope.parentFor(nestedChild)==null,"nested cleanup");
  });
  check(CornerQuadParentScope.parentFor(child)==null,"outer cleanup");
  try{CornerQuadParentScope.emit(child,parent,()->{throw new IllegalStateException("expected");});}catch(IllegalStateException expected){}
  check(CornerQuadParentScope.parentFor(child)==null,"exception cleanup");
  try{CornerQuadParentScope.emit(child,child,()->{});throw new AssertionError("same identity allowed");}catch(IllegalArgumentException expected){}
  System.out.println("PASS: identity matching, nested restoration, exception cleanup, thread isolation, no self-parent");
 }
}
