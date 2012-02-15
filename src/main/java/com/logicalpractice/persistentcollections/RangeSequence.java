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

/**
 *
 */
public class RangeSequence implements Sequence<Integer> {
    private final int from;
    private final int to;
    private int step = 1;

    public RangeSequence(int from, int to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public Integer first() {
        return from;
    }

    @Override
    public Sequence<Integer> rest() {
        if( from >= to ) return null;
        return new RangeSequence(from + step, to);
    }
}
