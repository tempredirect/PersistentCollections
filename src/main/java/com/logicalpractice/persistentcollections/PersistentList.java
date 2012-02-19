package com.logicalpractice.persistentcollections;

import java.util.List;
import java.util.RandomAccess;

/**
 *
 */
public interface PersistentList<T> extends List<T>, RandomAccess {

   PersistentList<T> withSet(int i, T val);

   PersistentList<T> withAppended(T val);

   TransientList<T> toTransientList();
}
