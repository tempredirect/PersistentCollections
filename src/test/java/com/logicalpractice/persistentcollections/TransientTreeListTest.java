package com.logicalpractice.persistentcollections;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class TransientTreeListTest {
   
   @Test
   public void testRemoveAtEnd() throws Exception {
      TransientList<Integer> testObject = PersistentLists.create(1,2).toTransientList();
      
      Integer value = testObject.remove(1);
      
      assertThat(value, equalTo(2));
      assertThat(testObject.size(), equalTo(1));
      assertThat(testObject.get(0), equalTo(1));
   }

   @Test
   public void testRemoveInTheMiddle() throws Exception {
      TransientList<Integer> testObject = PersistentLists.create(1,2,3,4,5,6).toTransientList();

      Integer value = testObject.remove(3);

      assertThat(value, equalTo(4));
      assertThat(testObject.size(), equalTo(5));
      assertThat(testObject.get(3), equalTo(5));
   }
}
