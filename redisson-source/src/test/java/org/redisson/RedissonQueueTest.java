package org.redisson;

import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

import org.junit.Assert;
import org.junit.Test;
import org.redisson.api.RQueue;

import static org.assertj.core.api.Assertions.assertThat;

public class RedissonQueueTest extends BaseTest {

    @Test
    public void testAddOfferOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        assertThat(queue).containsExactly(1, 2, 3, 4);
        Assert.assertEquals((Integer)1, queue.poll());
        assertThat(queue).containsExactly(2, 3, 4);
        Assert.assertEquals((Integer)2, queue.element());
    }

    @Test
    public void testAddOffer() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.add(1);
        queue.offer(2);
        queue.add(3);
        queue.offer(4);

        assertThat(queue).containsExactly(1, 2, 3, 4);
        Assert.assertEquals((Integer)1, queue.poll());
        assertThat(queue).containsExactly(2, 3, 4);
        Assert.assertEquals((Integer)2, queue.element());
    }

    @Test
    public void testRemoveOrigin() {
        Queue<Integer> queue = new LinkedList<Integer>();
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        assertThat(queue).containsExactly(3, 4);
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test
    public void testRemove() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.add(1);
        queue.add(2);
        queue.add(3);
        queue.add(4);

        queue.remove();
        queue.remove();

        assertThat(queue).containsExactly(3, 4);
        queue.remove();
        queue.remove();

        Assert.assertTrue(queue.isEmpty());
    }

    @Test(expected = NoSuchElementException.class)
    public void testRemoveEmpty() {
        RQueue<Integer> queue = redisson.getQueue("queue");
        queue.remove();
    }

}
