package com.logicalpractice.persistentcollections;

import java.util.List;

/**
 *
 */
public interface TransientList<T> extends List<T> {
   PersistentList<T> toPersistentList();
}
