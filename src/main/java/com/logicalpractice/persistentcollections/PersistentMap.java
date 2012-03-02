package com.logicalpractice.persistentcollections;

import java.util.Map;

/**
 *
 */
public interface PersistentMap<K,V> extends Map<K,V> {
   
   PersistentMap<K,V> with(K key, V value);

   TransientMap<K,V> toTransientMap();
}
