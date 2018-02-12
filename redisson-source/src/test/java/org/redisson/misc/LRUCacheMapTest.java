package org.redisson.misc;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.redisson.cache.Cache;
import org.redisson.cache.LRUCacheMap;

import static org.assertj.core.api.Assertions.assertThat;

public class LRUCacheMapTest {

    @Test
    public void testMaxIdleTimeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LRUCacheMap<Integer, Integer>(2, 0, 0);
        map.put(1, 0, 0, TimeUnit.MILLISECONDS, 400, TimeUnit.MILLISECONDS);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(200);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(410);
        assertThat(map.keySet()).isEmpty();
    }

    @Test
    public void testTTLEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LRUCacheMap<Integer, Integer>(2, 0, 0);
        map.put(1, 0, 500, TimeUnit.MILLISECONDS, 0, TimeUnit.MILLISECONDS);
        assertThat(map.get(1)).isEqualTo(0);
        Thread.sleep(100);
        assertThat(map.get(1)).isEqualTo(0);
        assertThat(map.keySet()).containsOnly(1);
        Thread.sleep(500);
        assertThat(map.keySet()).isEmpty();
    }

    @Test
    public void testSizeLRUEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LRUCacheMap<Integer, Integer>(10, 0, 0);
        map.put(1, 0);
        map.put(2, 0);
        map.put(3, 0);
        map.put(4, 0);
        map.put(5, 0);
        map.put(6, 0);
        map.put(7, 0);
        map.put(8, 0);
        map.put(9, 0);
        map.put(10, 0);

        map.get(1);
        map.put(13, 0);
        
        assertThat(map.keySet()).contains(13, 1).hasSize(10);
        
        map.get(1);
        map.put(14, 0);
        
        assertThat(map.keySet()).contains(14, 1).hasSize(10);
    }
    
    @Test
    public void testSizeEviction() throws InterruptedException {
        Cache<Integer, Integer> map = new LRUCacheMap<Integer, Integer>(2, 0, 0);
        map.put(1, 0);
        map.put(2, 0);
        
        assertThat(map.keySet()).containsOnly(1, 2);
        
        map.put(3, 0);
        
        assertThat(map.keySet()).contains(3).hasSize(2);
        
        map.put(4, 0);
        
        assertThat(map.keySet()).containsOnly(4, 3);
    }
    
}
