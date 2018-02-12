/**
 * Copyright 2016 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api;

import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.Codec;

/**
 * Interface for using pipeline feature.
 *
 * All method invocations on objects
 * from this interface are batched to separate queue and could be executed later
 * with <code>execute()</code> method.
 *
 *
 * @author Nikita Koksharov
 *
 */
public interface RBatchReactive {

    /**
     * Returns set-based cache instance by <code>name</code>.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of value
     * @param name - name of object
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name);

    /**
     * Returns set-based cache instance by <code>name</code>
     * using provided <code>codec</code> for values.
     * Uses map (value_hash, value) under the hood for minimal memory consumption.
     * Supports value eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getSet(String, Codec)}.</p>
     *
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for values
     * @return SetCache object
     */
    <V> RSetCacheReactive<V> getSetCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>
     * using provided <code>codec</code> for both cache keys and values.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String, Codec)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @param codec - codec for keys and values
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name, Codec codec);

    /**
     * Returns map-based cache instance by <code>name</code>.
     * Supports entry eviction with a given TTL value.
     *
     * <p>If eviction is not required then it's better to use regular map {@link #getMap(String)}.</p>
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return MapCache object
     */
    <K, V> RMapCacheReactive<K, V> getMapCache(String name);

    /**
     * Returns object holder by name
     *
     * @param <V> type of value
     * @param name - name of object
     * @return Bucket object
     */
    <V> RBucketReactive<V> getBucket(String name);

    <V> RBucketReactive<V> getBucket(String name, Codec codec);

    /**
     * Returns HyperLogLog object by name
     *
     * @param <V> type of value
     * @param name - name of object
     * @return HyperLogLog object
     */
    <V> RHyperLogLogReactive<V> getHyperLogLog(String name);

    <V> RHyperLogLogReactive<V> getHyperLogLog(String name, Codec codec);

    /**
     * Returns list instance by name.
     *
     * @param <V> type of value
     * @param name - name of object
     * @return List object
     */
    <V> RListReactive<V> getList(String name);

    <V> RListReactive<V> getList(String name, Codec codec);

    /**
     * Returns map instance by name.
     *
     * @param <K> type of key
     * @param <V> type of value
     * @param name - name of object
     * @return Map object
     */
    <K, V> RMapReactive<K, V> getMap(String name);

    <K, V> RMapReactive<K, V> getMap(String name, Codec codec);

    /**
     * Returns set instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Set object
     */
    <V> RSetReactive<V> getSet(String name);

    <V> RSetReactive<V> getSet(String name, Codec codec);

    /**
     * Returns topic instance by name.
     *
     * @param <M> type of message
     * @param name - name of object
     * @return Topic object
     */
    <M> RTopicReactive<M> getTopic(String name);

    <M> RTopicReactive<M> getTopic(String name, Codec codec);

    /**
     * Returns queue instance by name.
     *
     * @param <V> type of value
     * @param name - name of object
     * @return Queue object
     */
    <V> RQueueReactive<V> getQueue(String name);

    <V> RQueueReactive<V> getQueue(String name, Codec codec);

    /**
     * Returns blocking queue instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return BlockingQueue object
     */
    <V> RBlockingQueueReactive<V> getBlockingQueue(String name);

    <V> RBlockingQueueReactive<V> getBlockingQueue(String name, Codec codec);

    /**
     * Returns deque instance by name.
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return Deque object
     */
    <V> RDequeReactive<V> getDequeReactive(String name);

    <V> RDequeReactive<V> getDequeReactive(String name, Codec codec);

    /**
     * Returns "atomic long" instance by name.
     * 
     * @param name - name of object
     * @return AtomicLong object
     */
    RAtomicLongReactive getAtomicLongReactive(String name);

    /**
     * Returns Redis Sorted Set instance by name
     * 
     * @param <V> type of value
     * @param name - name of object
     * @return ScoredSortedSet object
     */
    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name);

    <V> RScoredSortedSetReactive<V> getScoredSortedSet(String name, Codec codec);

    /**
     * Returns String based Redis Sorted Set instance by name
     * All elements are inserted with the same score during addition,
     * in order to force lexicographical ordering
     *
     * @param name - name of object
     * @return LexSortedSet object
     */
    RLexSortedSetReactive getLexSortedSet(String name);

    /**
     * Returns bitSet instance by name.
     *
     * @param name of bitSet
     * @return BitSet object
     */
    RBitSetReactive getBitSet(String name);

    /**
     * Returns script operations object
     *
     * @return Script object
     */
    RScriptReactive getScript();

    /**
     * Returns keys operations.
     * Each of Redis/Redisson object associated with own key
     *
     * @return Keys object
     */
    RKeysReactive getKeys();

    /**
     * Executes all operations accumulated during Reactive methods invocations Reactivehronously.
     *
     * In cluster configurations operations grouped by slot ids
     * so may be executed on different servers. Thus command execution order could be changed
     *
     * @return List with result object for each command
     */
    Publisher<BatchResult<?>> execute();

    /**
     * Command replies are skipped such approach saves response bandwidth.
     * <p>
     * NOTE: Redis 3.2+ required
     * 
     * @return self instance
     */
    RBatchReactive skipResult();
    
    /**
     * 
     * <p>
     * NOTE: Redis 3.0+ required
     * 
     * @param slaves number to sync
     * @param timeout for sync operation
     * @param unit value
     * @return self instance
     */
    RBatchReactive syncSlaves(int slaves, long timeout, TimeUnit unit);
    
    /**
     * Defines timeout for Redis response. 
     * Starts to countdown when Redis command has been successfully sent.
     * <p>
     * <code>0</code> value means use <code>Config.setTimeout</code> value instead.
     * <p>
     * Default is <code>0</code>
     * 
     * @param timeout value
     * @param unit value
     * @return self instance
     */
    RBatchReactive timeout(long timeout, TimeUnit unit);

    /**
     * Defines time interval for another one attempt send Redis commands batch 
     * if it hasn't been sent already.
     * <p>
     * <code>0</code> value means use <code>Config.setRetryInterval</code> value instead.
     * <p>
     * Default is <code>0</code>
     * 
     * @param retryInterval value
     * @param unit value
     * @return self instance
     */
    RBatchReactive retryInterval(long retryInterval, TimeUnit unit);

    /**
     * Defines attempts amount to re-send Redis commands batch
     * if it hasn't been sent already.
     * <p>
     * <code>0</code> value means use <code>Config.setRetryAttempts</code> value instead.
     * <p>
     * Default is <code>0</code>
     * 
     * @param retryAttempts value
     * @return self instance
     */
    RBatchReactive retryAttempts(int retryAttempts);
    
}
