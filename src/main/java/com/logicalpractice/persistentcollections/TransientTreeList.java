package com.logicalpractice.persistentcollections;

import java.util.AbstractList;
import java.util.RandomAccess;
import java.util.concurrent.atomic.AtomicReference;

/**
*
*/
final class TransientTreeList<T> extends AbstractList<T> implements TransientList<T>, RandomAccess {

   private int cnt;
   private int shift;
   private PersistentLists.Node root;
   private Object[] tail;

   TransientTreeList(int cnt, int shift, PersistentLists.Node root, Object[] tail) {
      this.cnt = cnt;
      this.shift = shift;
      this.root = root;
      this.tail = tail;
   }

   PersistentLists.Node ensureEditable(PersistentLists.Node node) {
      if (node.edit == root.edit)
         return node;
      return new PersistentLists.Node(root.edit, node.array.clone());
   }

   void ensureEditable() {
      Thread owner = root.edit.get();
      if (owner == Thread.currentThread())
         return;
      if (owner != null)
         throw new IllegalAccessError("Transient used by non-owner thread");
      throw new IllegalAccessError("Transient used after persistent! call");
   }

   @Override
   public PersistentList<T> toPersistentList() {
      ensureEditable();
      root.edit.set(null);
      Object[] trimmedTail = new Object[cnt - tailoff()];
      System.arraycopy(tail, 0, trimmedTail, 0, trimmedTail.length);
      return new PersistentTreeList<T>(cnt, shift, root, trimmedTail);
   }

   public boolean add(T val) {
      ensureEditable();
      int i = cnt;
      //room in tail?
      if (i - tailoff() < 32) {
         tail[i & 0x01f] = val;
         ++cnt;
         return true;
      }
      //full tail, push into tree
      PersistentLists.Node newroot;
      PersistentLists.Node tailnode = new PersistentLists.Node(root.edit, tail);
      tail = new Object[32];
      tail[0] = val;
      int newshift = shift;
      //overflow root?
      if ((cnt >>> 5) > (1 << shift)) {
         newroot = new PersistentLists.Node(root.edit);
         newroot.array[0] = root;
         newroot.array[1] = PersistentLists.newPath(root.edit, shift, tailnode);
         newshift += 5;
      } else
         newroot = pushTail(shift, root, tailnode);
      root = newroot;
      shift = newshift;
      ++cnt;
      return true;
   }

   private PersistentLists.Node pushTail(int level, PersistentLists.Node parent, PersistentLists.Node tailnode) {
      //if parent is leaf, insert node,
      // else does it map to an existing child? -> nodeToInsert = pushNode one more level
      // else alloc new path
      //return  nodeToInsert placed in parent
      parent = ensureEditable(parent);
      int subidx = ((cnt - 1) >>> level) & 0x01f;
      PersistentLists.Node ret = parent;
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

   private int tailoff() {
      if (cnt < 32)
         return 0;
      return ((cnt - 1) >>> 5) << 5;
   }

   private Object[] arrayFor(int i) {
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
   public T set(int i, T val) {
      ensureEditable();
      T previous = get(i);
      if (i >= 0 && i < cnt) {
         if (i >= tailoff()) {
            tail[i & 0x01f] = val;
            return previous;
         }

         root = doAssoc(shift, root, i, val);
         return previous;
      }
      throw new IndexOutOfBoundsException();
   }

   private PersistentLists.Node doAssoc(int level, PersistentLists.Node node, int i, Object val) {
      node = ensureEditable(node);
      PersistentLists.Node ret = node;
      if (level == 0) {
         ret.array[i & 0x01f] = val;
      } else {
         int subidx = (i >>> level) & 0x01f;
         ret.array[subidx] = doAssoc(level - 5, (PersistentLists.Node) node.array[subidx], i, val);
      }
      return ret;
   }

   @Override
   public T remove(int index) {
      ensureEditable();
      if (cnt == 0) {
         throw new IllegalStateException("Can't pop empty vector");
      }
      T lastItem = get(index);
      int toSave = cnt - 1 - index ;
      Object [] saved = null;
      if ( toSave > 0 ) {
         saved = new Object[toSave];
         for( int i = 0 ; i < toSave; i ++ ){
            saved[i] = get(index + i + 1);
         }
      }
      while( cnt != index ){
         pop();
      }
      if ( saved != null ){
         for (Object o : saved) {
            add((T)o);
         }
      }
      return lastItem;
   }

   private void pop() {
      if (cnt == 1) {
         cnt = 0;
         return ;
      }
      int i = cnt - 1;
      //pop in tail?
      if ((i & 0x01f) > 0) {
         --cnt;
         return ;
      }

      Object[] newtail = arrayFor(cnt - 2);

      PersistentLists.Node newroot = popTail(shift, root);
      int newshift = shift;
      if (newroot == null) {
         newroot = new PersistentLists.Node(root.edit);
      }
      if (shift > 5 && newroot.array[1] == null) {
         newroot = ensureEditable((PersistentLists.Node) newroot.array[0]);
         newshift -= 5;
      }
      root = newroot;
      shift = newshift;
      --cnt;
      tail = newtail;
   }
   
   private PersistentLists.Node popTail(int level, PersistentLists.Node node) {
      node = ensureEditable(node);
      int subidx = ((cnt - 2) >>> level) & 0x01f;
      if (level > 5) {
         PersistentLists.Node newchild = popTail(level - 5, (PersistentLists.Node) node.array[subidx]);
         if (newchild == null && subidx == 0)
            return null;
         else {
            PersistentLists.Node ret = node;
            ret.array[subidx] = newchild;
            return ret;
         }
      } else if (subidx == 0)
         return null;
      else {
         PersistentLists.Node ret = node;
         ret.array[subidx] = null;
         return ret;
      }
   }

   /**
    * {@inheritDoc}
    *
    * @throws IndexOutOfBoundsException {@inheritDoc}
    */
   @Override
   public T get(int index) {
      ensureEditable();
      Object[] node = arrayFor(index);
      return (T) node[index & 0x01f];
   }

   @Override
   public int size() {
      ensureEditable();
      return cnt;
   }
}
