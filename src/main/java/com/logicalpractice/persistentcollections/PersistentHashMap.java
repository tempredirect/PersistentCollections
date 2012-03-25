package com.logicalpractice.persistentcollections;


//import clojure.lang.Cons;
//import clojure.lang.ISeq;

import clojure.lang.ISeq;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

//import clojure.lang.IMapEntry;
//import clojure.lang.TransientMap;

/**
 * PersistentHashMap implementation of PersistentMap.
 * <p/>
 * Orginially created by Rich Hicky in the clojure as PersistentTreeMap.
 * quote from original PersistentTreeMap implementation in clojure:
 *
 * A persistent rendition of Phil Bagwell's Hash Array Mapped Trie
 *
 * Uses path copying for persistence
 * HashCollision leaves vs. extended hashing
 * Node polymorphism vs. conditionals
 * No sub-tree pools or root-resizing
 * Any errors are my own
 *
 *
 */
public class PersistentHashMap<K, V> extends AbstractMap<K, V> implements PersistentMap<K, V> {

   private static class MapEntry<K, V> implements Entry<K, V> {
      private final K key;
      private final V value;

      private MapEntry(K key, V value) {
         this.key = key;
         this.value = value;
      }

      public K getKey() {
         return key;
      }

      public V getValue() {
         return value;
      }

      @Override
      public V setValue(V value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         MapEntry mapEntry = (MapEntry) o;

         return key.equals(mapEntry.key) && !(value != null ? !value.equals(mapEntry.value) : mapEntry.value != null);
      }

      @Override
      public int hashCode() {
         int result = key.hashCode();
         result = 31 * result + (value != null ? value.hashCode() : 0);
         return result;
      }

      @Override
      public String toString() {
         return "MapEntry{" +
               "key=" + key +
               ", value=" + value +
               '}';
      }
   }

   public static class Box {

      public Object val;

      public Box(Object val) {
         this.val = val;
      }
   }

   private final int count;
   private final MapNode root;
   private final boolean hasNull;
   private final Object nullValue;

   private final static PersistentHashMap EMPTY = new PersistentHashMap(0, null, false, null);

   private final static Object NOT_FOUND = new Object();

   @SuppressWarnings("unchecked")
   private static <K, V> PersistentMap<K, V> empty() {
      return EMPTY;
   }

   public static <K, V> PersistentMap<K, V> create(Map<K, V> other) {
      TransientMap<K, V> ret = PersistentHashMap.<K, V>empty().toTransientMap();

      for (Entry<K, V> entry : other.entrySet()) {
         ret.put(entry.getKey(), entry.getValue());
      }
      return ret.toPersistentMap();
   }


   PersistentHashMap(int count, MapNode root, boolean hasNull, Object nullValue) {
      this.count = count;
      this.root = root;
      this.hasNull = hasNull;
      this.nullValue = nullValue;
   }

   public boolean containsKey(Object key) {
      if (key == null)
         return hasNull;

      return (root != null) && root.find(0, Util.hashCode(key), key, NOT_FOUND) != NOT_FOUND;
   }

//public Map.Entry<K,V> entryAt(Object key){
//	if(key == null)
//		return hasNull ? new MapEntry<K,V>(null, nullValue) : null;
//	return (root != null) ? root.find(0, Util.hashCode(key), key) : null;
//}

   public PersistentHashMap<K, V> with(K key, V val) {
      if (key == null) {
         throw new IllegalArgumentException("Null keys are not allowed");
      }
      Box addedLeaf = new Box(null);
      MapNode newroot = (root == null ? BitmapIndexedNode.EMPTY : root)
            .assoc(0, Util.hashCode(key), key, val, addedLeaf);
      if (newroot == root)
         return this;
      return new PersistentHashMap<K, V>(addedLeaf.val == null ? count : count + 1, newroot, hasNull, nullValue);
   }

   @SuppressWarnings("unchecked")
   public V get(Object key) {
      if (key == null)
         return null; // cannot contain null

      return root != null ? (V) root.find(0, Util.hashCode(key), key, null) : null;
   }
   //public Object valAt(Object key, Object notFound){
   //	if(key == null)
   //		return hasNull ? nullValue : notFound;
   //	return root != null ? root.find(0, Util.hashCode(key), key, notFound) : notFound;
   //}
   //
   //public Object valAt(Object key){
   //	return valAt(key, null);
   //}


   public PersistentHashMap<K, V> without(Object key) {
      if (key == null)
         return this;

      if (root == null)
         return this;

      MapNode newroot = root.without(0, Util.hashCode(key), key);
      if (newroot == root)
         return this;

      return new PersistentHashMap<K, V>(count - 1, newroot, hasNull, nullValue);
   }

//public Iterator iterator(){
//	return new SeqIterator(seq());
//}

   public int size() {
      return count;
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return new AbstractSet<Entry<K, V>>() {
         @Override
         public Iterator<Entry<K, V>> iterator() {
            return PersistentHashMap.this.root.iterator();
         }

         @Override
         public int size() {
            return PersistentHashMap.this.size();
         }
      };
   }

//   public ISeq seq() {
//      ISeq s = root != null ? root.nodeSeq() : null;
//      return hasNull ? new Cons(new MapEntry(null, nullValue), s) : s;
//   }


   static int mask(int hash, int shift) {
      //return ((hash << shift) >>> 27);// & 0x01f;
      return (hash >>> shift) & 0x01f;
   }

   @Override
   public TransientHashMap<K, V> toTransientMap() {
      return new TransientHashMap<K, V>(this);
   }


   static final class TransientHashMap<K, V> extends AbstractMap<K,V> implements TransientMap<K, V> {

      AtomicReference<Thread> edit;
      MapNode root;
      int count;
      boolean hasNull;
      Object nullValue;
      final Box leafFlag = new Box(null);


      TransientHashMap(PersistentHashMap m) {
         this(new AtomicReference<Thread>(Thread.currentThread()), m.root, m.count, m.hasNull, m.nullValue);
      }

      TransientHashMap(AtomicReference<Thread> edit, MapNode root, int count, boolean hasNull, Object nullValue) {
         this.edit = edit;
         this.root = root;
         this.count = count;
         this.hasNull = hasNull;
         this.nullValue = nullValue;
      }

      TransientMap doAssoc(Object key, Object val) {
         if (key == null) {
            if (this.nullValue != val)
               this.nullValue = val;
            if (!hasNull) {
               this.count++;
               this.hasNull = true;
            }
            return this;
         }
//		Box leafFlag = new Box(null);
         leafFlag.val = null;
         MapNode n = (root == null ? BitmapIndexedNode.EMPTY : root)
               .assoc(edit, 0, Util.hashCode(key), key, val, leafFlag);
         if (n != this.root)
            this.root = n;
         if (leafFlag.val != null) this.count++;
         return this;
      }

      TransientMap doWithout(Object key) {
         if (key == null) {
            if (!hasNull) return this;
            hasNull = false;
            nullValue = null;
            this.count--;
            return this;
         }
         if (root == null) return this;
//		Box leafFlag = new Box(null);
         leafFlag.val = null;
         MapNode n = root.without(edit, 0, Util.hashCode(key), key, leafFlag);
         if (n != root)
            this.root = n;
         if (leafFlag.val != null) this.count--;
         return this;
      }

      @Override
      public PersistentMap<K,V> toPersistentMap() {
         edit.set(null);
         return new PersistentHashMap(count, root, hasNull, nullValue);
      }

      Object doValAt(Object key, Object notFound) {
         if (key == null)
            if (hasNull)
               return nullValue;
            else
               return notFound;
         if (root == null)
            return null;
         return root.find(0, Util.hashCode(key), key, notFound);
      }

      int doCount() {
         return count;
      }

      void ensureEditable() {
         Thread owner = edit.get();
         if (owner == Thread.currentThread())
            return;
         if (owner != null)
            throw new IllegalAccessError("Transient used by non-owner thread");
         throw new IllegalAccessError("Transient used after persistent! call");
      }

      public Set<Map.Entry<K,V>> entrySet(){
         return new AbstractSet<Entry<K, V>>() {
            @Override
            public Iterator<Entry<K, V>> iterator() {
               return TransientHashMap.this.root.iterator();
            }
            @Override
            public int size() {
               return TransientHashMap.this.size();
            }
         };
      }
   }

   static interface MapNode<K, V> extends Serializable {

      MapNode<K, V> assoc(int shift, int hash, K key, V val, Box addedLeaf);

      MapNode<K, V> without(int shift, int hash, K key);

      MapNode<K, V> without(AtomicReference<Thread> edit, int shift, int hash, K key, Box removedLeaf);

      Map.Entry<K, V> find(int shift, int hash, Object key);

      V find(int shift, int hash, Object key, V notFound);

      ISeq nodeSeq();

      Iterator<Map.Entry<K,V>> iterator();

      MapNode<K, V> assoc(AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box addedLeaf);

   }

   final static class ArrayNode<K, V> implements MapNode<K, V> {
      int count;
      final MapNode<K, V>[] array;
      final AtomicReference<Thread> edit;

      ArrayNode(AtomicReference<Thread> edit, int count, MapNode<K, V>[] array) {
         this.array = array;
         this.edit = edit;
         this.count = count;
      }

      public MapNode<K, V> assoc(int shift, int hash, K key, V val, Box addedLeaf) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null)
            return new ArrayNode<K, V>(null, count + 1, cloneAndSet(array, idx, BitmapIndexedNode.<K, V>empty().assoc(shift + 5, hash, key, val, addedLeaf)));
         MapNode<K, V> n = node.assoc(shift + 5, hash, key, val, addedLeaf);
         if (n == node)
            return this;
         return new ArrayNode<K, V>(null, count, cloneAndSet(array, idx, n));
      }

      public MapNode<K, V> without(int shift, int hash, K key) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null)
            return this;
         MapNode<K, V> n = node.without(shift + 5, hash, key);
         if (n == node)
            return this;
         if (n == null) {
            if (count <= 8) // shrink
               return pack(null, idx);
            return new ArrayNode<K, V>(null, count - 1, cloneAndSet(array, idx, n));
         } else
            return new ArrayNode<K, V>(null, count, cloneAndSet(array, idx, n));
      }

      public Map.Entry<K, V> find(int shift, int hash, Object key) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null)
            return null;
         return node.find(shift + 5, hash, key);
      }

      public V find(int shift, int hash, Object key, V notFound) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null)
            return notFound;
         return node.find(shift + 5, hash, key, notFound);
      }

      public ISeq nodeSeq() {
         //return PersistentTreeMap.Seq.create(array);
         throw new UnsupportedOperationException();
      }

      @Override
      public Iterator<Map.Entry<K,V>> iterator() {
         throw new UnsupportedOperationException();
      }

      private ArrayNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
         if (this.edit == edit)
            return this;
         return new ArrayNode<K, V>(edit, count, this.array.clone());
      }

      private ArrayNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, MapNode<K, V> n) {
         ArrayNode<K, V> editable = ensureEditable(edit);
         editable.array[i] = n;
         return editable;
      }


      private MapNode<K, V> pack(AtomicReference<Thread> edit, int idx) {
         Object[] newArray = new Object[2 * (count - 1)];
         int j = 1;
         int bitmap = 0;
         for (int i = 0; i < idx; i++)
            if (array[i] != null) {
               newArray[j] = array[i];
               bitmap |= 1 << i;
               j += 2;
            }
         for (int i = idx + 1; i < array.length; i++)
            if (array[i] != null) {
               newArray[j] = array[i];
               bitmap |= 1 << i;
               j += 2;
            }
         return new BitmapIndexedNode<K, V>(edit, bitmap, newArray);
      }

      public MapNode<K, V> assoc(AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box addedLeaf) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null) {
            ArrayNode<K, V> editable = editAndSet(edit, idx, BitmapIndexedNode.<K, V>empty().assoc(edit, shift + 5, hash, key, val, addedLeaf));
            editable.count++;
            return editable;
         }
         MapNode<K, V> n = node.assoc(edit, shift + 5, hash, key, val, addedLeaf);
         if (n == node)
            return this;
         return editAndSet(edit, idx, n);
      }

      public MapNode<K, V> without(AtomicReference<Thread> edit, int shift, int hash, K key, Box removedLeaf) {
         int idx = mask(hash, shift);
         MapNode<K, V> node = array[idx];
         if (node == null)
            return this;
         MapNode<K, V> n = node.without(edit, shift + 5, hash, key, removedLeaf);
         if (n == node)
            return this;
         if (n == null) {
            if (count <= 8) // shrink
               return pack(edit, idx);
            ArrayNode<K, V> editable = editAndSet(edit, idx, n);
            editable.count--;
            return editable;
         }
         return editAndSet(edit, idx, n);
      }
   }

   final static class BitmapIndexedNode<K, V> implements MapNode<K, V> {
      static final BitmapIndexedNode EMPTY = new BitmapIndexedNode(null, 0, new Object[0]);

      @SuppressWarnings("unchecked")
      static final <K, V> BitmapIndexedNode<K, V> empty() {
         return EMPTY;
      }


      int bitmap;
      Object[] array;
      final AtomicReference<Thread> edit;

      @Override
      public Iterator<Map.Entry<K,V>> iterator() {
         throw new UnsupportedOperationException();
      }

      final int index(int bit) {
         return Integer.bitCount(bitmap & (bit - 1));
      }

      BitmapIndexedNode(AtomicReference<Thread> edit, int bitmap, Object[] array) {
         this.bitmap = bitmap;
         this.array = array;
         this.edit = edit;
      }

      public MapNode<K, V> assoc(int shift, int hash, K key, V val, Box addedLeaf) {
         int bit = bitpos(hash, shift);
         int idx = index(bit);
         if ((bitmap & bit) != 0) {
            Object keyOrNull = array[2 * idx];
            Object valOrNode = array[2 * idx + 1];
            if (keyOrNull == null) {
               MapNode<K, V> n = ((MapNode<K, V>) valOrNode).assoc(shift + 5, hash, key, val, addedLeaf);
               if (n == valOrNode)
                  return this;
               return new BitmapIndexedNode<K, V>(null, bitmap, cloneAndSet(array, 2 * idx + 1, n));
            }
            if (Util.equals(key, keyOrNull)) {
               if (val == valOrNode)
                  return this;
               return new BitmapIndexedNode<K, V>(null, bitmap, cloneAndSet(array, 2 * idx + 1, val));
            }
            addedLeaf.val = addedLeaf;
            return new BitmapIndexedNode<K, V>(null, bitmap,
                  cloneAndSet(array,
                        2 * idx, null,
                        2 * idx + 1, createNode(shift + 5, keyOrNull, valOrNode, hash, key, val)));
         } else {
            int n = Integer.bitCount(bitmap);
            if (n >= 16) {
               MapNode<K, V>[] nodes = new MapNode[32];
               int jdx = mask(hash, shift);
               nodes[jdx] = BitmapIndexedNode.<K, V>empty().assoc(shift + 5, hash, key, val, addedLeaf);
               int j = 0;
               for (int i = 0; i < 32; i++)
                  if (((bitmap >>> i) & 1) != 0) {
                     if (array[j] == null)
                        nodes[i] = (MapNode<K, V>) array[j + 1];
                     else
                        nodes[i] = BitmapIndexedNode.<K, V>empty().assoc(shift + 5, Util.hashCode(array[j]), (K) array[j], (V) array[j + 1], addedLeaf);
                     j += 2;
                  }
               return new ArrayNode<K, V>(null, n + 1, nodes);
            } else {
               Object[] newArray = new Object[2 * (n + 1)];
               System.arraycopy(array, 0, newArray, 0, 2 * idx);
               newArray[2 * idx] = key;
               addedLeaf.val = addedLeaf;
               newArray[2 * idx + 1] = val;
               System.arraycopy(array, 2 * idx, newArray, 2 * (idx + 1), 2 * (n - idx));
               return new BitmapIndexedNode<K, V>(null, bitmap | bit, newArray);
            }
         }
      }

      public MapNode<K, V> without(int shift, int hash, K key) {
         int bit = bitpos(hash, shift);
         if ((bitmap & bit) == 0)
            return this;
         int idx = index(bit);
         Object keyOrNull = array[2 * idx];
         Object valOrNode = array[2 * idx + 1];
         if (keyOrNull == null) {
            MapNode<K, V> n = ((MapNode<K, V>) valOrNode).without(shift + 5, hash, key);
            if (n == valOrNode)
               return this;
            if (n != null)
               return new BitmapIndexedNode<K, V>(null, bitmap, cloneAndSet(array, 2 * idx + 1, n));
            if (bitmap == bit)
               return null;
            return new BitmapIndexedNode<K, V>(null, bitmap ^ bit, removePair(array, idx));
         }
         if (Util.equals(key, keyOrNull))
            // TODO: collapse
            return new BitmapIndexedNode<K, V>(null, bitmap ^ bit, removePair(array, idx));
         return this;
      }

      public Map.Entry<K, V> find(int shift, int hash, Object key) {
         int bit = bitpos(hash, shift);
         if ((bitmap & bit) == 0)
            return null;
         int idx = index(bit);
         Object keyOrNull = array[2 * idx];
         Object valOrNode = array[2 * idx + 1];
         if (keyOrNull == null)
            return ((MapNode<K, V>) valOrNode).find(shift + 5, hash, key);
         if (Util.equals(key, keyOrNull))
            return new MapEntry<K, V>((K) keyOrNull, (V) valOrNode);
         return null;
      }

      public V find(int shift, int hash, Object key, V notFound) {
         int bit = bitpos(hash, shift);
         if ((bitmap & bit) == 0)
            return notFound;
         int idx = index(bit);
         Object keyOrNull = array[2 * idx];
         Object valOrNode = array[2 * idx + 1];
         if (keyOrNull == null)
            return ((MapNode<K, V>) valOrNode).find(shift + 5, hash, key, notFound);
         if (Util.equals(key, keyOrNull))
            return (V) valOrNode;
         return notFound;
      }

      public ISeq nodeSeq() {
//		return NodeSeq.create(array);
         throw new UnsupportedOperationException();
      }

      private BitmapIndexedNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
         if (this.edit == edit)
            return this;
         int n = Integer.bitCount(bitmap);
         Object[] newArray = new Object[n >= 0 ? 2 * (n + 1) : 4]; // make room for next assoc
         System.arraycopy(array, 0, newArray, 0, 2 * n);
         return new BitmapIndexedNode<K, V>(edit, bitmap, newArray);
      }

      private BitmapIndexedNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, Object a) {
         BitmapIndexedNode<K, V> editable = ensureEditable(edit);
         editable.array[i] = a;
         return editable;
      }

      private BitmapIndexedNode<K, V> editAndSet(AtomicReference<Thread> edit, int i, Object a, int j, Object b) {
         BitmapIndexedNode<K, V> editable = ensureEditable(edit);
         editable.array[i] = a;
         editable.array[j] = b;
         return editable;
      }

      private BitmapIndexedNode<K, V> editAndRemovePair(AtomicReference<Thread> edit, int bit, int i) {
         if (bitmap == bit)
            return null;
         BitmapIndexedNode<K, V> editable = ensureEditable(edit);
         editable.bitmap ^= bit;
         System.arraycopy(editable.array, 2 * (i + 1), editable.array, 2 * i, editable.array.length - 2 * (i + 1));
         editable.array[editable.array.length - 2] = null;
         editable.array[editable.array.length - 1] = null;
         return editable;
      }

      public MapNode<K, V> assoc(AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box addedLeaf) {
         int bit = bitpos(hash, shift);
         int idx = index(bit);
         if ((bitmap & bit) != 0) {
            Object keyOrNull = array[2 * idx];
            Object valOrNode = array[2 * idx + 1];
            if (keyOrNull == null) {
               MapNode<K, V> n = ((MapNode<K, V>) valOrNode).assoc(edit, shift + 5, hash, key, val, addedLeaf);
               if (n == valOrNode)
                  return this;
               return editAndSet(edit, 2 * idx + 1, n);
            }
            if (Util.equals(key, keyOrNull)) {
               if (val == valOrNode)
                  return this;
               return editAndSet(edit, 2 * idx + 1, val);
            }
            addedLeaf.val = addedLeaf;
            return editAndSet(edit, 2 * idx, null, 2 * idx + 1,
                  createNode(edit, shift + 5, keyOrNull, valOrNode, hash, key, val));
         } else {
            int n = Integer.bitCount(bitmap);
            if (n * 2 < array.length) {
               addedLeaf.val = addedLeaf;
               BitmapIndexedNode<K, V> editable = ensureEditable(edit);
               System.arraycopy(editable.array, 2 * idx, editable.array, 2 * (idx + 1), 2 * (n - idx));
               editable.array[2 * idx] = key;
               editable.array[2 * idx + 1] = val;
               editable.bitmap |= bit;
               return editable;
            }
            if (n >= 16) {
               MapNode<K, V>[] nodes = new MapNode[32];
               int jdx = mask(hash, shift);
               nodes[jdx] = BitmapIndexedNode.<K, V>empty().assoc(edit, shift + 5, hash, key, val, addedLeaf);
               int j = 0;
               for (int i = 0; i < 32; i++)
                  if (((bitmap >>> i) & 1) != 0) {
                     if (array[j] == null)
                        nodes[i] = (MapNode<K, V>) array[j + 1];
                     else
                        nodes[i] = BitmapIndexedNode.<K, V>empty().assoc(edit, shift + 5, Util.hashCode(array[j]), (K) array[j], (V) array[j + 1], addedLeaf);
                     j += 2;
                  }
               return new ArrayNode<K, V>(edit, n + 1, nodes);
            } else {
               Object[] newArray = new Object[2 * (n + 4)];
               System.arraycopy(array, 0, newArray, 0, 2 * idx);
               newArray[2 * idx] = key;
               addedLeaf.val = addedLeaf;
               newArray[2 * idx + 1] = val;
               System.arraycopy(array, 2 * idx, newArray, 2 * (idx + 1), 2 * (n - idx));
               BitmapIndexedNode<K, V> editable = ensureEditable(edit);
               editable.array = newArray;
               editable.bitmap |= bit;
               return editable;
            }
         }
      }

      public MapNode<K, V> without(AtomicReference<Thread> edit, int shift, int hash, K key, Box removedLeaf) {
         int bit = bitpos(hash, shift);
         if ((bitmap & bit) == 0)
            return this;
         int idx = index(bit);
         Object keyOrNull = array[2 * idx];
         Object valOrNode = array[2 * idx + 1];
         if (keyOrNull == null) {
            MapNode<K, V> n = ((MapNode<K, V>) valOrNode).without(edit, shift + 5, hash, key, removedLeaf);
            if (n == valOrNode)
               return this;
            if (n != null)
               return editAndSet(edit, 2 * idx + 1, n);
            if (bitmap == bit)
               return null;
            removedLeaf.val = removedLeaf;
            return editAndRemovePair(edit, bit, idx);
         }
         if (Util.equals(key, keyOrNull)) {
            removedLeaf.val = removedLeaf;
            // TODO: collapse
            return editAndRemovePair(edit, bit, idx);
         }
         return this;
      }
   }

   final static class HashCollisionNode<K, V> implements MapNode<K, V> {

      final int hash;
      int count;
      Object[] array;
      final AtomicReference<Thread> edit;

      HashCollisionNode(AtomicReference<Thread> edit, int hash, int count, Object... array) {
         this.edit = edit;
         this.hash = hash;
         this.count = count;
         this.array = array;
      }

      @Override
      public Iterator<Map.Entry<K,V>> iterator() {
         throw new UnsupportedOperationException();
      }

      public MapNode<K, V> assoc(int shift, int hash, K key, V val, Box addedLeaf) {
         if (hash == this.hash) {
            int idx = findIndex(key);
            if (idx != -1) {
               if (array[idx + 1] == val)
                  return this;
               return new HashCollisionNode<K, V>(null, hash, count, cloneAndSet(array, idx + 1, val));
            }
            Object[] newArray = new Object[array.length + 2];
            System.arraycopy(array, 0, newArray, 0, array.length);
            newArray[array.length] = key;
            newArray[array.length + 1] = val;
            addedLeaf.val = addedLeaf;
            return new HashCollisionNode<K, V>(edit, hash, count + 1, newArray);
         }
         // nest it in a bitmap node
         return new BitmapIndexedNode<K, V>(null, bitpos(this.hash, shift), new Object[]{null, this})
               .assoc(shift, hash, key, val, addedLeaf);
      }

      public MapNode<K, V> without(int shift, int hash, K key) {
         int idx = findIndex(key);
         if (idx == -1)
            return this;
         if (count == 1)
            return null;
         return new HashCollisionNode<K, V>(null, hash, count - 1, removePair(array, idx / 2));
      }

      public Map.Entry<K, V> find(int shift, int hash, Object key) {
         int idx = findIndex(key);
         if (idx < 0)
            return null;
         if (Util.equals(key, array[idx]))
            return new MapEntry<K, V>((K) array[idx], (V) array[idx + 1]);
         return null;
      }

      public V find(int shift, int hash, Object key, V notFound) {
         int idx = findIndex(key);
         if (idx < 0)
            return notFound;
         if (Util.equals(key, array[idx]))
            return (V) array[idx + 1];
         return notFound;
      }

      public ISeq nodeSeq() {
//		return NodeSeq.create(array);
         throw new UnsupportedOperationException();
      }

      public int findIndex(Object key) {
         for (int i = 0; i < 2 * count; i += 2) {
            if (Util.equals(key, array[i]))
               return i;
         }
         return -1;
      }

      private HashCollisionNode<K, V> ensureEditable(AtomicReference<Thread> edit) {
         if (this.edit == edit)
            return this;
         return new HashCollisionNode<K, V>(edit, hash, count, array);
      }

      private HashCollisionNode ensureEditable(AtomicReference<Thread> edit, int count, Object[] array) {
         if (this.edit == edit) {
            this.array = array;
            this.count = count;
            return this;
         }
         return new HashCollisionNode(edit, hash, count, array);
      }

      private HashCollisionNode editAndSet(AtomicReference<Thread> edit, int i, Object a) {
         HashCollisionNode editable = ensureEditable(edit);
         editable.array[i] = a;
         return editable;
      }

      private HashCollisionNode editAndSet(AtomicReference<Thread> edit, int i, Object a, int j, Object b) {
         HashCollisionNode editable = ensureEditable(edit);
         editable.array[i] = a;
         editable.array[j] = b;
         return editable;
      }


      public MapNode<K,V> assoc(AtomicReference<Thread> edit, int shift, int hash, K key, V val, Box addedLeaf) {
         if (hash == this.hash) {
            int idx = findIndex(key);
            if (idx != -1) {
               if (array[idx + 1] == val)
                  return this;
               return editAndSet(edit, idx + 1, val);
            }
            if (array.length > 2 * count) {
               addedLeaf.val = addedLeaf;
               HashCollisionNode editable = editAndSet(edit, 2 * count, key, 2 * count + 1, val);
               editable.count++;
               return editable;
            }
            Object[] newArray = new Object[array.length + 2];
            System.arraycopy(array, 0, newArray, 0, array.length);
            newArray[array.length] = key;
            newArray[array.length + 1] = val;
            addedLeaf.val = addedLeaf;
            return ensureEditable(edit, count + 1, newArray);
         }
         // nest it in a bitmap node
         return new BitmapIndexedNode(edit, bitpos(this.hash, shift), new Object[]{null, this, null, null})
               .assoc(edit, shift, hash, key, val, addedLeaf);
      }

      public MapNode<K,V> without(AtomicReference<Thread> edit, int shift, int hash, K key, Box removedLeaf) {
         int idx = findIndex(key);
         if (idx == -1)
            return this;
         if (count == 1)
            return null;
         HashCollisionNode editable = ensureEditable(edit);
         editable.array[idx] = editable.array[2 * count - 2];
         editable.array[idx + 1] = editable.array[2 * count - 1];
         editable.array[2 * count - 2] = editable.array[2 * count - 1] = null;
         editable.count--;
         return editable;
      }
   }

/*
public static void main(String[] args){
	try
		{
		ArrayList words = new ArrayList();
		Scanner s = new Scanner(new File(args[0]));
		s.useDelimiter(Pattern.compile("\\W"));
		while(s.hasNext())
			{
			String word = s.next();
			words.add(word);
			}
		System.out.println("words: " + words.size());
		IPersistentMap map = PersistentHashMap.EMPTY;
		//IPersistentMap map = new PersistentTreeMap();
		//Map ht = new Hashtable();
		Map ht = new HashMap();
		Random rand;

		System.out.println("Building map");
		long startTime = System.nanoTime();
		for(Object word5 : words)
			{
			map = map.assoc(word5, word5);
			}
		rand = new Random(42);
		IPersistentMap snapshotMap = map;
		for(int i = 0; i < words.size() / 200; i++)
			{
			map = map.without(words.get(rand.nextInt(words.size() / 2)));
			}
		long estimatedTime = System.nanoTime() - startTime;
		System.out.println("count = " + map.count() + ", time: " + estimatedTime / 1000000);

		System.out.println("Building ht");
		startTime = System.nanoTime();
		for(Object word1 : words)
			{
			ht.put(word1, word1);
			}
		rand = new Random(42);
		for(int i = 0; i < words.size() / 200; i++)
			{
			ht.remove(words.get(rand.nextInt(words.size() / 2)));
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("count = " + ht.size() + ", time: " + estimatedTime / 1000000);

		System.out.println("map lookup");
		startTime = System.nanoTime();
		int c = 0;
		for(Object word2 : words)
			{
			if(!map.contains(word2))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		System.out.println("ht lookup");
		startTime = System.nanoTime();
		c = 0;
		for(Object word3 : words)
			{
			if(!ht.containsKey(word3))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		System.out.println("snapshotMap lookup");
		startTime = System.nanoTime();
		c = 0;
		for(Object word4 : words)
			{
			if(!snapshotMap.contains(word4))
				++c;
			}
		estimatedTime = System.nanoTime() - startTime;
		System.out.println("notfound = " + c + ", time: " + estimatedTime / 1000000);
		}
	catch(FileNotFoundException e)
		{
		e.printStackTrace();
		}

}
*/

   private static <K, V> MapNode<K, V>[] cloneAndSet(MapNode<K, V>[] array, int i, MapNode<K, V> a) {
      MapNode<K, V>[] clone = array.clone();
      clone[i] = a;
      return clone;
   }

   private static Object[] cloneAndSet(Object[] array, int i, Object a) {
      Object[] clone = array.clone();
      clone[i] = a;
      return clone;
   }

   private static Object[] cloneAndSet(Object[] array, int i, Object a, int j, Object b) {
      Object[] clone = array.clone();
      clone[i] = a;
      clone[j] = b;
      return clone;
   }

   private static Object[] removePair(Object[] array, int i) {
      Object[] newArray = new Object[array.length - 2];
      System.arraycopy(array, 0, newArray, 0, 2 * i);
      System.arraycopy(array, 2 * (i + 1), newArray, 2 * i, newArray.length - 2 * i);
      return newArray;
   }

   private static <K, V> MapNode<K, V> createNode(int shift, K key1, V val1, int key2hash, K key2, V val2) {
      int key1hash = Util.hashCode(key1);
      if (key1hash == key2hash)
         return new HashCollisionNode<K, V>(null, key1hash, 2, new Object[]{key1, val1, key2, val2});
      Box _ = new Box(null);
      AtomicReference<Thread> edit = new AtomicReference<Thread>();
      return BitmapIndexedNode.<K, V>empty()
            .assoc(edit, shift, key1hash, key1, val1, _)
            .assoc(edit, shift, key2hash, key2, val2, _);
   }

   private static <K, V> MapNode<K, V> createNode(AtomicReference<Thread> edit, int shift, Object key1, Object val1, int key2hash, Object key2, Object val2) {
      int key1hash = Util.hashCode(key1);
      if (key1hash == key2hash)
         return new HashCollisionNode(null, key1hash, 2, new Object[]{key1, val1, key2, val2});
      Box _ = new Box(null);
      return BitmapIndexedNode.EMPTY
            .assoc(edit, shift, key1hash, key1, val1, _)
            .assoc(edit, shift, key2hash, key2, val2, _);
   }

   private static int bitpos(int hash, int shift) {
      return 1 << mask(hash, shift);
   }
}