package com.logicalpractice.persistentcollections;

import org.junit.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 *
 */
public class PersistentTreeListTest {

   @Test
   public void create() throws Exception {
      PersistentList<Integer> testObject = PersistentLists.create(1, 2, 3, 4);

      assertThat(testObject.size(), equalTo(4));
   }

   @Test
   public void withAppend() throws Exception {
      PersistentList<Integer> testObject = PersistentLists.create(1);
      
      PersistentList<Integer> result = testObject.withAppended(2);

      assertThat(result, not(sameInstance(testObject)));
   }

   @Test
   public void testCons80() throws Exception {
      
      PersistentList<Integer> result = PersistentLists.create(0);
      for(int i = 1; i < 80 ; i++)
         result = result.withAppended(i);

      assertThat(result.size(), equalTo(80));
   }
}
