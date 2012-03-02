package com.logicalpractice.persistentcollections;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class UtilTest {
   
   @Test
   public void testEquals() throws Exception {
      assertTrue(Util.equals(null, null));
      assertTrue(Util.equals("Thing", "Thing")); // same reference

      assertFalse(Util.equals("Thing", null));
      assertFalse(Util.equals(null, "null"));
   }

   @SuppressWarnings("RedundantStringConstructorCall")
   @Test
   public void testEquals2() throws Exception {
      assertTrue(Util.equals(new String("Thing"), new String("Thing"))); // not same reference
      assertFalse(Util.equals(new String("foo"), new String("bar")));
   }

   @Test
   public void testHashCode() throws Exception {

   }
}
