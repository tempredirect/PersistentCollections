package com.logicalpractice.persistentcollections;

import java.util.Map;

/**
 *
 */
public interface TransientMap<K,V> extends Map<K,V> {

   PersistentMap<K,V> toPersistentMap();
}
