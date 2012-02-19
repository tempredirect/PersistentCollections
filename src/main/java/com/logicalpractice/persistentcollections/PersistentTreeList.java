package com.logicalpractice.persistentcollections;

import java.io.Serializable;
import java.util.AbstractList;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public final class PersistentTreeList<T> extends AbstractList<T> implements PersistentList<T>, Serializable {

   private final int cnt;
   private final int shift;
   private final PersistentLists.Node root;
   private final Object[] tail;

   PersistentTreeList(int cnt, int shift, PersistentLists.Node root, Object[] tail) {
      this.cnt = cnt;
      this.shift = shift;
      this.root = root;
      this.tail = tail;
   }

   private static PersistentLists.Node editableRoot(PersistentLists.Node node) {
      return new PersistentLists.Node(new AtomicReference<Thread>(Thread.currentThread()), node.array.clone());
   }

   private static Object[] editableTail(Object[] tl) {
      Object[] ret = new Object[32];
      System.arraycopy(tl, 0, ret, 0, tl.length);
      return ret;
   }

   public TransientTreeList<T> toTransientList() {
      return new TransientTreeList<T>(this.cnt, this.shift, editableRoot(this.root), editableTail(this.tail));
   }

   final int tailoff() {
      if (cnt < 32)
         return 0;
      return ((cnt - 1) >>> 5) << 5;
   }

   public Object[] arrayFor(int i) {
      if (i >= 0 && i < cnt) {
         if (i >= tailoff())
            return tail;
         PersistentLists.Node node = root;
         for (int level = shift; level > 0; level -= 5)
            node = (PersistentLists.Node) node.array[(i >>> level) & 0x01f];
         return node.array;
      }
      throw new IndexOutOfBoundsException();
   }

   @Override
   public PersistentList<T> withSet(int i, T val) {
      if (i >= 0 && i < cnt) {
         if (i >= tailoff()) {
            Object[] newTail = new Object[tail.length];
            System.arraycopy(tail, 0, newTail, 0, tail.length);
            newTail[i & 0x01f] = val;

            return new PersistentTreeList<T>(cnt, shift, root, newTail);
         }

         return new PersistentTreeList<T>(cnt, shift, PersistentLists.doAssoc(shift, root, i, val), tail);
      }
      if (i == cnt)
         return withAppended(val);
      throw new IndexOutOfBoundsException();
   }

   @Override
   public PersistentList<T> withAppended(T val) {
      int i = cnt;
      //room in tail?
      //	if(tail.length < 32)
      if (cnt - tailoff() < 32) {
         Object[] newTail = new Object[tail.length + 1];
         System.arraycopy(tail, 0, newTail, 0, tail.length);
         newTail[tail.length] = val;
         return new PersistentTreeList<T>(cnt + 1, shift, root, newTail);
      }
      //full tail, push into tree
      PersistentLists.Node newroot;
      PersistentLists.Node tailnode = new PersistentLists.Node(root.edit, tail);
      int newshift = shift;
      //overflow root?
      if ((cnt >>> 5) > (1 << shift)) {
         newroot = new PersistentLists.Node(root.edit);
         newroot.array[0] = root;
         newroot.array[1] = PersistentLists.newPath(root.edit, shift, tailnode);
         newshift += 5;
      } else
         newroot = pushTail(shift, root, tailnode);
      return new PersistentTreeList<T>(cnt + 1, newshift, newroot, new Object[]{val});
   }

   private PersistentLists.Node pushTail(int level, PersistentLists.Node parent, PersistentLists.Node tailnode) {
      //if parent is leaf, insert node,
      // else does it map to an existing child? -> nodeToInsert = pushNode one more level
      // else alloc new path
      //return  nodeToInsert placed in copy of parent
      int subidx = ((cnt - 1) >>> level) & 0x01f;
      PersistentLists.Node ret = new PersistentLists.Node(parent.edit, parent.array.clone());
      PersistentLists.Node nodeToInsert;
      if (level == 5) {
         nodeToInsert = tailnode;
      } else {
         PersistentLists.Node child = (PersistentLists.Node) parent.array[subidx];
         nodeToInsert = (child != null) ?
               pushTail(level - 5, child, tailnode)
               : PersistentLists.newPath(root.edit, level - 5, tailnode);
      }
      ret.array[subidx] = nodeToInsert;
      return ret;
   }

   public PersistentList<T> pop() {
      if (cnt == 0)
         throw new IllegalStateException("Can't pop empty vector");
      if (cnt == 1)
         return PersistentLists.EMPTY;
      //if(tail.length > 1)
      if (cnt - tailoff() > 1) {
         Object[] newTail = new Object[tail.length - 1];
         System.arraycopy(tail, 0, newTail, 0, newTail.length);
         return new PersistentTreeList(cnt - 1, shift, root, newTail);
      }
      Object[] newtail = arrayFor(cnt - 2);

      PersistentLists.Node newroot = popTail(shift, root);
      int newshift = shift;
      if (newroot == null) {
         newroot = PersistentLists.EMPTY_NODE;
      }
      if (shift > 5 && newroot.array[1] == null) {
         newroot = (PersistentLists.Node) newroot.array[0];
         newshift -= 5;
      }
      return new PersistentTreeList<T>(cnt - 1, newshift, newroot, newtail);
   }

   private PersistentLists.Node popTail(int level, PersistentLists.Node node) {
      int subidx = ((cnt - 2) >>> level) & 0x01f;
      if (level > 5) {
         PersistentLists.Node newchild = popTail(level - 5, (PersistentLists.Node) node.array[subidx]);
         if (newchild == null && subidx == 0)
            return null;
         else {
            PersistentLists.Node ret = new PersistentLists.Node(root.edit, node.array.clone());
            ret.array[subidx] = newchild;
            return ret;
         }
      } else if (subidx == 0)
         return null;
      else {
         PersistentLists.Node ret = new PersistentLists.Node(root.edit, node.array.clone());
         ret.array[subidx] = null;
         return ret;
      }
   }

   /**
    * {@inheritDoc}
    *
    * @throws IndexOutOfBoundsException {@inheritDoc}
    */
   @SuppressWarnings("unchecked")
   @Override
   public T get(int index) {
      Object[] node = arrayFor(index);
      return (T) node[index & 0x01f];
   }

   @Override
   public int size() {
      return cnt;
   }

}
   


