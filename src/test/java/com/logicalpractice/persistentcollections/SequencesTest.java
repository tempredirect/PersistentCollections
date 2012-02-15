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

import org.junit.Test;
import sun.swing.text.CountingPrintable;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class SequencesTest {
    
    @Test
    public void testIterable() throws Exception {
        Iterable<Integer> iterable = Sequences.iterable(new RangeSequence(1,10));

        int count = 0;
        for (Integer i : iterable) {
            count ++;
        }

        assertThat(count, equalTo(10));

        // should be repeatable
        count = 0;
        for (Integer i : iterable) {
            count ++;
        }

        assertThat(count, equalTo(10));
    }
}
