Persistent Collections
======================

This is a port of the clojure collection classes to be more approachable from a pure java project.

 * New PersistentList, TransientList



Implemented features
--------------------

 * PersistentTreeList - Immutable List implementation with structural sharing
 * TransientTreeList - Mutable partner to PersistentTreeList


Missing/Known issues
--------------------

TransientTreeList isn't a complete list implementation:
 * only add and remove are implemented
 * clear()/subList().clear() haven't been implemented
 * iterator().remove()
 * It doesn't fail fast with ConcurrentModificationException's althrough it may not be nessary as it is only
   editable by a single thread
