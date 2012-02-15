/*
 * Copyright (c) Logical Practice Systems. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package com.logicalpractice.persistentcollections;

import java.util.Iterator;

/**
 */
public class Sequences {

    public static <T> Iterable<T> iterable(Sequence<T> sequence){
        return new IterableSequence<T>(sequence);
    }
    
    public static <T> Iterable<T> headlessIterable(Sequence<T> sequence){
        return new HeadlessIterable<T>(sequence);
    }

    private static class IterableSequence<T> implements Iterable<T> {

        private final Sequence<T> sequence;

        public IterableSequence(Sequence<T> sequence) {
            this.sequence = sequence;
        }

        @Override
        public Iterator<T> iterator() {
            return new SequenceIterator<T>(sequence);
        }
    }
    
    private static class HeadlessIterable<T> implements Iterable<T> {

        private Sequence<T> sequence;

        public HeadlessIterable(Sequence<T> sequence) {
            this.sequence = sequence;
        }

        @Override
        public Iterator<T> iterator() {
            if( sequence == null ) throw new IllegalStateException("iterator() has been called more than once");

            Iterator<T> iter = new SequenceIterator<T>(sequence);
            sequence = null; // dereference the sequence
            return iter;
        }
    }

    private static class SequenceIterator<T> implements Iterator<T> {
        
        private Sequence<T> sequence;

        public SequenceIterator(Sequence<T> sequence) {
            this.sequence = sequence;
        }

        @Override
        public boolean hasNext() {
            return sequence != null; 
        }

        @Override
        public T next() {
            T next = sequence.first();
            sequence = sequence.rest();
            return next;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
