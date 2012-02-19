package com.logicalpractice.persistentcollections;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
public class PersistentLists {
   private final static AtomicReference<Thread> NOEDIT = new AtomicReference<Thread>(null);
   final static Node EMPTY_NODE = new Node(NOEDIT, new Object[32]);
   public final static PersistentTreeList EMPTY = new PersistentTreeList(0, 5, EMPTY_NODE, new Object[]{});

   @SuppressWarnings("unchecked")
   static public <T> PersistentList<T> emptyPersistentList() { return EMPTY ;}

   static public <T> PersistentList<T> create(Sequence<T> items) {
      TransientList<T> ret = PersistentLists.<T>emptyPersistentList().toTransientList();
      for (; items != null; items = items.rest())
         ret.add(items.first());
      return ret.toPersistentList();
   }

   static public <T> PersistentList<T> create(Iterable<T> items) {
      TransientList<T> ret = PersistentLists.<T>emptyPersistentList().toTransientList();
      for (T item : items)
         ret.add(item);
      return ret.toPersistentList();
   }

   static public <T> PersistentList<T> create(T... items) {
      TransientList<T> ret = PersistentLists.<T>emptyPersistentList().toTransientList();
      for (T item : items)
         ret.add(item);
      return ret.toPersistentList();
   }

   static Node doAssoc(int level, Node node, int i, Object val) {
      Node ret = new Node(node.edit, node.array.clone());
      if (level == 0) {
         ret.array[i & 0x01f] = val;
      } else {
         int subidx = (i >>> level) & 0x01f;
         ret.array[subidx] = doAssoc(level - 5, (Node) node.array[subidx], i, val);
      }
      return ret;
   }

   static Node newPath(AtomicReference<Thread> edit, int level, Node node) {
      if (level == 0)
         return node;
      Node ret = new Node(edit);
      ret.array[0] = newPath(edit, level - 5, node);
      return ret;
   }

   static class Node implements Serializable {
      transient final AtomicReference<Thread> edit;
      final Object[] array;

      Node(AtomicReference<Thread> edit, Object[] array) {
         this.edit = edit;
         this.array = array;
      }

      Node(AtomicReference<Thread> edit) {
         this.edit = edit;
         this.array = new Object[32];
      }
   }
}
