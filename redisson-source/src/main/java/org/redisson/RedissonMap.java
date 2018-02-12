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
package org.redisson;

import java.math.BigDecimal;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.redisson.api.MapOptions;
import org.redisson.api.MapOptions.WriteMode;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.mapreduce.RMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.MapScanCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.NumberConvertor;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.MapGetAllDecoder;
import org.redisson.mapreduce.RedissonMapReduce;
import org.redisson.misc.Hash;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * Distributed and concurrent implementation of {@link java.util.concurrent.ConcurrentMap}
 * and {@link java.util.Map}
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMap<K, V> extends RedissonExpirable implements RMap<K, V> {

    final AtomicInteger writeBehindCurrentThreads = new AtomicInteger();
    final Queue<Runnable> writeBehindTasks;
    final RedissonClient redisson;
    final MapOptions<K, V> options;
    
    protected RedissonMap(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options) {
        super(commandExecutor, name);
        this.redisson = redisson;
        this.options = options;
        if (options != null && options.getWriteMode() == WriteMode.WRITE_BEHIND) {
            writeBehindTasks = new ConcurrentLinkedQueue<Runnable>();
        } else {
            writeBehindTasks = null;
        }
    }

    public RedissonMap(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson, MapOptions<K, V> options) {
        super(codec, commandExecutor, name);
        this.redisson = redisson;
        this.options = options;
        if (options != null && options.getWriteMode() == WriteMode.WRITE_BEHIND) {
            writeBehindTasks = new ConcurrentLinkedQueue<Runnable>();
        } else {
            writeBehindTasks = null;
        }
    }
    
    @Override
    public <KOut, VOut> RMapReduce<K, V, KOut, VOut> mapReduce() {
        return new RedissonMapReduce<K, V, KOut, VOut>(this, redisson, commandExecutor.getConnectionManager());
    }

    @Override
    public RLock getLock(K key) {
        String lockName = getLockName(key);
        return redisson.getLock(lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(K key) {
        String lockName = getLockName(key);
        return redisson.getReadWriteLock(lockName);
    }
    
    private String getLockName(Object key) {
        ByteBuf keyState = encodeMapKey(key);
        try {
            return suffixName(getName(), Hash.hashToBase64(keyState) + ":key");
        } finally {
            keyState.release();
        }
    }
    
    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HLEN, getName());
    }

    @Override
    public int valueSize(K key) {
        return get(valueSizeAsync(key));
    }
    
    @Override
    public RFuture<Integer> valueSizeAsync(K key) {
        checkKey(key);
        
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HSTRLEN, getName(key), encodeMapKey(key));
    }

    protected void checkKey(Object key) {
        if (key == null) {
            throw new NullPointerException("map key can't be null");
        }
    }
    
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        return get(containsKeyAsync(key));
    }

    @Override
    public RFuture<Boolean> containsKeyAsync(Object key) {
        checkKey(key);
        
        return commandExecutor.readAsync(getName(key), codec, RedisCommands.HEXISTS, getName(key), encodeMapKey(key));
    }

    @Override
    public boolean containsValue(Object value) {
        return get(containsValueAsync(value));
    }

    @Override
    public RFuture<Boolean> containsValueAsync(Object value) {
        checkValue(value);
        
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local s = redis.call('hvals', KEYS[1]);" +
                        "for i = 1, #s, 1 do "
                            + "if ARGV[1] == s[i] then "
                                + "return 1 "
                            + "end "
                       + "end;" +
                     "return 0",
                Collections.<Object>singletonList(getName()), encodeMapValue(value));
    }

    @Override
    public Map<K, V> getAll(Set<K> keys) {
        return get(getAllAsync(keys));
    }

    @Override
    public RFuture<Map<K, V>> getAllAsync(final Set<K> keys) {
        if (keys.isEmpty()) {
            return newSucceededFuture(Collections.<K, V>emptyMap());
        }

        RFuture<Map<K, V>> future = getAllOperationAsync(keys);
        if (hasNoLoader()) {
            return future;
        }

        final RPromise<Map<K, V>> result = new RedissonPromise<Map<K, V>>();
        future.addListener(new FutureListener<Map<K, V>>() {
            @Override
            public void operationComplete(final Future<Map<K, V>> mapFuture) throws Exception {
                if (!mapFuture.isSuccess()) {
                    result.tryFailure(mapFuture.cause());
                    return;
                }
                
                if (!mapFuture.getNow().keySet().containsAll(keys)) {
                    Set<K> newKeys = new HashSet<K>(keys);
                    newKeys.removeAll(mapFuture.getNow().keySet());
                    
                    loadAllAsync(newKeys, false, 1, mapFuture.getNow()).addListener(new FutureListener<Void>() {
                        @Override
                        public void operationComplete(Future<Void> future) throws Exception {
                            result.trySuccess(mapFuture.getNow());
                        }
                    });
                } else {
                    result.trySuccess(mapFuture.getNow());
                }
            }
        });
        return result;
    }

    protected boolean hasNoLoader() {
        return options == null || options.getLoader() == null;
    }

    protected RFuture<Map<K, V>> getAllOperationAsync(Set<K> keys) {
        List<Object> args = new ArrayList<Object>(keys.size() + 1);
        args.add(getName());
        encodeMapKeys(args, keys);
        RFuture<Map<K, V>> future = commandExecutor.readAsync(getName(), codec, new RedisCommand<Map<Object, Object>>("HMGET", new MapGetAllDecoder(new ArrayList<Object>(keys), 0), ValueType.MAP_VALUE), 
                args.toArray());
        return future;
    }
    
    @Override
    public V get(Object key) {
        return get(getAsync((K)key));
    }

    @Override
    public V put(K key, V value) {
        return get(putAsync(key, value));
    }

    @Override
    public V remove(Object key) {
        return get(removeAsync((K)key));
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> map) {
        get(putAllAsync(map));
    }

    @Override
    public RFuture<Void> putAllAsync(final Map<? extends K, ? extends V> map) {
        if (map.isEmpty()) {
            return newSucceededFuture(null);
        }

        RFuture<Void> future = putAllOperationAsync(map);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<Void> listener = new MapWriterTask<Void>() {
            @Override
            public void execute() {
                options.getWriter().writeAll((Map<K, V>) map);
            }
        };
        return mapWriterFuture(future, listener);
    }

    protected <M> RFuture<M> mapWriterFuture(RFuture<M> future, MapWriterTask<M> listener) {
        if (options != null && options.getWriteMode() == WriteMode.WRITE_BEHIND) {
            future.addListener(new MapWriteBehindListener<M>(commandExecutor, listener, writeBehindCurrentThreads, writeBehindTasks, options.getWriteBehindThreads()));
            return future;
        }        

        return new MapWriterPromise<M>(future, commandExecutor, listener);
    }

    protected RFuture<Void> putAllOperationAsync(Map<? extends K, ? extends V> map) {
        List<Object> params = new ArrayList<Object>(map.size()*2 + 1);
        params.add(getName());
        for (java.util.Map.Entry<? extends K, ? extends V> t : map.entrySet()) {
            if (t.getKey() == null) {
                throw new NullPointerException("map key can't be null");
            }
            if (t.getValue() == null) {
                throw new NullPointerException("map value can't be null");
            }

            params.add(encodeMapKey(t.getKey()));
            params.add(encodeMapValue(t.getValue()));
        }

        RFuture<Void> future = commandExecutor.writeAsync(getName(), codec, RedisCommands.HMSET, params.toArray());
        return future;
    }

    @Override
    public void clear() {
        delete();
    }

    @Override
    public Set<K> keySet() {
        return keySet(null);
    }
    
    public Set<K> keySet(String pattern) {
        return new KeySet(pattern);
    }

    @Override
    public Collection<V> values() {
        return values(null);
    }

    public Collection<V> values(String keyPattern) {
        return new Values(keyPattern);
    }
    
    @Override
    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return entrySet(null);
    }
    
    public Set<java.util.Map.Entry<K, V>> entrySet(String keyPattern) {
        return new EntrySet(keyPattern);
    }

    @Override
    public Set<K> readAllKeySet() {
        return get(readAllKeySetAsync());
    }

    @Override
    public RFuture<Set<K>> readAllKeySetAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HKEYS, getName());
    }

    @Override
    public Collection<V> readAllValues() {
        return get(readAllValuesAsync());
    }

    @Override
    public RFuture<Collection<V>> readAllValuesAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HVALS, getName());
    }

    @Override
    public Set<Entry<K, V>> readAllEntrySet() {
        return get(readAllEntrySetAsync());
    }

    @Override
    public RFuture<Set<Entry<K, V>>> readAllEntrySetAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HGETALL_ENTRY, getName());
    }

    @Override
    public Map<K, V> readAllMap() {
        return get(readAllMapAsync());
    }

    @Override
    public RFuture<Map<K, V>> readAllMapAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.HGETALL, getName());
    }

    
    @Override
    public V putIfAbsent(K key, V value) {
        return get(putIfAbsentAsync(key, value));
    }

    @Override
    public RFuture<V> putIfAbsentAsync(final K key, final V value) {
        checkKey(key);
        checkValue(key);
        
        RFuture<V> future = putIfAbsentOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<V> listener = new MapWriterTask<V>() {
            @Override
            public void execute() {
                options.getWriter().write(key, value);
            }
            
            @Override
            protected boolean condition(Future<V> future) {
                return future.getNow() == null;
            }

        };
        return mapWriterFuture(future, listener);
    }

    protected boolean hasNoWriter() {
        return options == null || options.getWriter() == null;
    }

    protected RFuture<V> putIfAbsentOperationAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_MAP_VALUE,
                 "if redis.call('hsetnx', KEYS[1], ARGV[1], ARGV[2]) == 1 then "
                    + "return nil "
                + "else "
                    + "return redis.call('hget', KEYS[1], ARGV[1]) "
                + "end",
                Collections.<Object>singletonList(getName(key)), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPutIfAbsent(K key, V value) {
        return get(fastPutIfAbsentAsync(key, value));
    }

    @Override
    public RFuture<Boolean> fastPutIfAbsentAsync(final K key, final V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = fastPutIfAbsentOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<Boolean> listener = new MapWriterTask<Boolean>() {
            @Override
            public void execute() {
                options.getWriter().write(key, value);
            }
            
            @Override
            protected boolean condition(Future<Boolean> future) {
                return future.getNow();
            }

        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Boolean> fastPutIfAbsentOperationAsync(K key, V value) {
        return commandExecutor.writeAsync(getName(key), codec, RedisCommands.HSETNX, getName(key), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean remove(Object key, Object value) {
        return get(removeAsync(key, value));
    }

    @Override
    public RFuture<Boolean> removeAsync(final Object key, Object value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = removeOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<Boolean> listener = new MapWriterTask<Boolean>() {
            @Override
            public void execute() {
                options.getWriter().delete((K) key);
            }
            
            @Override
            protected boolean condition(Future<Boolean> future) {
                return future.getNow();
            }

        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Boolean> removeOperationAsync(Object key, Object value) {
        RFuture<Boolean> future = commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                        + "return redis.call('hdel', KEYS[1], ARGV[1]) "
                + "else "
                    + "return 0 "
                + "end",
            Collections.<Object>singletonList(getName(key)), encodeMapKey(key), encodeMapValue(value));
        return future;
    }

    protected void checkValue(Object value) {
        if (value == null) {
            throw new NullPointerException("map value can't be null");
        }
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        return get(replaceAsync(key, oldValue, newValue));
    }

    @Override
    public RFuture<Boolean> replaceAsync(final K key, V oldValue, final V newValue) {
        checkKey(key);
        if (oldValue == null) {
            throw new NullPointerException("map oldValue can't be null");
        }
        if (newValue == null) {
            throw new NullPointerException("map newValue can't be null");
        }

        RFuture<Boolean> future = replaceOperationAsync(key, oldValue, newValue);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<Boolean> listener = new MapWriterTask<Boolean>() {
            @Override
            public void execute() {
                options.getWriter().write(key, newValue);
            }
            
            @Override
            protected boolean condition(Future<Boolean> future) {
                return future.getNow();
            }

        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Boolean> replaceOperationAsync(K key, V oldValue, V newValue) {
        return commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('hget', KEYS[1], ARGV[1]) == ARGV[2] then "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[3]); "
                    + "return 1; "
                + "else "
                    + "return 0; "
                + "end",
                Collections.<Object>singletonList(getName(key)), encodeMapKey(key), encodeMapValue(oldValue), encodeMapValue(newValue));
    }

    @Override
    public V replace(K key, V value) {
        return get(replaceAsync(key, value));
    }

    @Override
    public RFuture<V> replaceAsync(final K key, final V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = replaceOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<V> listener = new MapWriterTask<V>() {
            @Override
            public void execute() {
                options.getWriter().write(key, value);
            }
            
            @Override
            protected boolean condition(Future<V> future) {
                return future.getNow() != null;
            }

        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> replaceOperationAsync(final K key, final V value) {
        return commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_MAP_VALUE,
                "if redis.call('hexists', KEYS[1], ARGV[1]) == 1 then "
                    + "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                    + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                    + "return v; "
                + "else "
                    + "return nil; "
                + "end",
            Collections.<Object>singletonList(getName(key)), encodeMapKey(key), encodeMapValue(value));
    }

    protected RFuture<V> getOperationAsync(K key) {
        return commandExecutor.readAsync(getName(key), codec, RedisCommands.HGET, getName(key), encodeMapKey(key));
    }
    
    @Override
    public RFuture<V> getAsync(final K key) {
        checkKey(key);

        RFuture<V> future = getOperationAsync(key);
        if (hasNoLoader()) {
            return future;
        }
        
        final RPromise<V> result = new RedissonPromise<V>();
        future.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow() == null) {
                    loadValue(key, result, false);
                } else {
                    result.trySuccess(future.getNow());
                }
            }
        });
        return result;
    }
    
    private RFuture<V> externalPutAsync(K key, V value) {
        return putAsync(key, value);
    }
    
    @Override
    public void loadAll(boolean replaceExistingValues, int parallelism) {
        get(loadAllAsync(replaceExistingValues, parallelism));
    }
    
    @Override
    public RFuture<Void> loadAllAsync(boolean replaceExistingValues, int parallelism) {
        return loadAllAsync(options.getLoader().loadAllKeys(), replaceExistingValues, parallelism, null);
    }
    
    @Override
    public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        get(loadAllAsync(keys, replaceExistingValues, parallelism));
    }
    
    @Override
    public RFuture<Void> loadAllAsync(final Set<? extends K> keys, boolean replaceExistingValues, int parallelism) {
        return loadAllAsync((Iterable<K>)keys, replaceExistingValues, parallelism, null);
    }
    
    private RFuture<Void> loadAllAsync(final Iterable<? extends K> keys, boolean replaceExistingValues, int parallelism, Map<K, V> loadedEntires) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("parallelism can't be lower than 1");
        }

        for (K key : keys) {
            checkKey(key);
        }
 
        final RPromise<Void> result = new RedissonPromise<Void>();
        final AtomicInteger counter = new AtomicInteger();
        final Iterator<? extends K> iter = keys.iterator();
        for (int i = 0; i < parallelism; i++) {
            if (!iter.hasNext()) {
                if (counter.get() == 0) {
                    result.trySuccess(null);
                }
                break;
            }
            
            counter.incrementAndGet();
            K key = iter.next();
            if (replaceExistingValues) {
                loadValue(result, counter, iter, key, loadedEntires);
            } else {
                checkAndLoadValue(result, counter, iter, key, loadedEntires);
            }
        }
        
        return result;
    }

    private void checkAndLoadValue(final RPromise<Void> result, final AtomicInteger counter, final Iterator<? extends K> iter,
            final K key, final Map<K, V> loadedEntires) {
        containsKeyAsync(key).addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (!future.getNow()) {
                    RPromise<V> promise = new RedissonPromise<V>();
                    promise.addListener(new FutureListener<V>() {
                        @Override
                        public void operationComplete(Future<V> future) throws Exception {
                            if (!future.isSuccess()) {
                                result.tryFailure(future.cause());
                                return;
                            }
                            
                            if (loadedEntires != null && future.getNow() != null) {
                                loadedEntires.put(key, future.getNow());
                            }
                            
                            checkAndLoadValue(result, counter, iter, loadedEntires);
                        }

                    });
                    loadValue(key, promise, false);
                } else {
                    checkAndLoadValue(result, counter, iter, loadedEntires);
                }
            }
        });
    }
    
    private void checkAndLoadValue(RPromise<Void> result, AtomicInteger counter, Iterator<? extends K> iter, Map<K, V> loadedEntires) {
        K key = null;
        synchronized (iter) {
            if (iter.hasNext()) {
                key = iter.next();
            }
        }
        
        if (key == null) {
            if (counter.decrementAndGet() == 0) {
                result.trySuccess(null);
            }
        } else if (!result.isDone()) {
            checkAndLoadValue(result, counter, iter, key, loadedEntires);
        }
    }
    
    private void loadValue(final RPromise<Void> result, final AtomicInteger counter, final Iterator<? extends K> iter,
            final K key, final Map<K, V> loadedEntires) {
        RPromise<V> promise = new RedissonPromise<V>();
        promise.addListener(new FutureListener<V>() {
            @Override
            public void operationComplete(Future<V> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (loadedEntires != null && future.getNow() != null) {
                    loadedEntires.put(key, future.getNow());
                }
                
                K key = null;
                synchronized (iter) {
                    if (iter.hasNext()) {
                        key = iter.next();
                    }
                }
                
                if (key == null) {
                    if (counter.decrementAndGet() == 0) {
                        result.trySuccess(null);
                    }
                } else if (!result.isDone()) {
                    loadValue(result, counter, iter, key, loadedEntires);
                }
            }
        });
        loadValue(key, promise, true);
    }
    
    @Override
    public RFuture<V> putAsync(final K key, final V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<V> future = putOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<V> listener = new MapWriterTask<V>() {
            @Override
            public void execute() {
                options.getWriter().write(key, value);
            }
        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> putOperationAsync(K key, V value) {
        return commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); "
                + "return v",
                Collections.<Object>singletonList(getName(key)), encodeMapKey(key), encodeMapValue(value));
    }


    @Override
    public RFuture<V> removeAsync(final K key) {
        checkKey(key);

        RFuture<V> future = removeOperationAsync(key);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<V> listener = new MapWriterTask<V>() {
            @Override
            public void execute() {
                options.getWriter().delete(key);
            }
        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> removeOperationAsync(K key) {
        return commandExecutor.evalWriteAsync(getName(key), codec, RedisCommands.EVAL_MAP_VALUE,
                "local v = redis.call('hget', KEYS[1], ARGV[1]); "
                + "redis.call('hdel', KEYS[1], ARGV[1]); "
                + "return v",
                Collections.<Object>singletonList(getName(key)), encodeMapKey(key));
    }

    @Override
    public RFuture<Boolean> fastPutAsync(final K key, final V value) {
        checkKey(key);
        checkValue(value);
        
        RFuture<Boolean> future = fastPutOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }
        
        MapWriterTask<Boolean> listener = new MapWriterTask<Boolean>() {
            @Override
            public void execute() {
                options.getWriter().write(key, value);
            }
        };
        return mapWriterFuture(future, listener);
    }

    protected RFuture<Boolean> fastPutOperationAsync(K key, V value) {
        return commandExecutor.writeAsync(getName(key), codec, RedisCommands.HSET, getName(key), encodeMapKey(key), encodeMapValue(value));
    }

    @Override
    public boolean fastPut(K key, V value) {
        return get(fastPutAsync(key, value));
    }

    @Override
    public RFuture<Long> fastRemoveAsync(final K ... keys) {
        if (keys == null) {
            throw new NullPointerException();
        }

        if (keys.length == 0) {
            return newSucceededFuture(0L);
        }

        if (hasNoWriter()) {
            return fastRemoveOperationAsync(keys);
        }

        RFuture<List<Long>> future = fastRemoveOperationBatchAsync(keys);            
        final RPromise<Long> result = new RedissonPromise<Long>();
        future.addListener(new FutureListener<List<Long>>() {
            @Override
            public void operationComplete(Future<List<Long>> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow().isEmpty()) {
                    result.trySuccess(0L);
                    return;
                }
                
                final List<K> deletedKeys = new ArrayList<K>();
                for (int i = 0; i < future.getNow().size(); i++) {
                    if (future.getNow().get(i) == 1) {
                        deletedKeys.add(keys[i]);
                    }
                }
                
                if (options.getWriteMode() == WriteMode.WRITE_BEHIND) {
                    result.trySuccess((long)deletedKeys.size());
                    
                    MapWriterTask<List<Long>> listener = new MapWriterTask<List<Long>>() {
                        @Override
                        public void execute() {
                            options.getWriter().deleteAll(deletedKeys);
                        }
                    };
                    future.addListener(new MapWriteBehindListener<List<Long>>(commandExecutor, listener, writeBehindCurrentThreads, writeBehindTasks, options.getWriteBehindThreads()));
                } else {
                    commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
                        @Override
                        public void run() {
                            options.getWriter().deleteAll(deletedKeys);
                            result.trySuccess((long)deletedKeys.size());
                        }
                    });
                }
            }
        });
        return result;
    }

    protected RFuture<List<Long>> fastRemoveOperationBatchAsync(final K... keys) {
        List<Object> args = new ArrayList<Object>(keys.length);
        for (K key : keys) {
            args.add(encodeMapKey(key));
        }

        RFuture<List<Long>> future = commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_LIST,
                        "local result = {}; " + 
                        "for i = 1, #ARGV, 1 do " 
                        + "local val = redis.call('hdel', KEYS[1], ARGV[i]); "
                        + "table.insert(result, val); "
                      + "end;"
                      + "return result;",
                        Arrays.<Object>asList(getName()), 
                        args.toArray());
        return future;
    }

    protected RFuture<Long> fastRemoveOperationAsync(K... keys) {
        List<Object> args = new ArrayList<Object>(keys.length + 1);
        args.add(getName());
        for (K key : keys) {
            args.add(encodeMapKey(key));
        }
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.HDEL, args.toArray());
    }

    @Override
    public long fastRemove(K ... keys) {
        return get(fastRemoveAsync(keys));
    }

    MapScanResult<ScanObjectEntry, ScanObjectEntry> scanIterator(String name, RedisClient client, long startPos, String pattern) {
        if (pattern == null) {
            RFuture<MapScanResult<ScanObjectEntry, ScanObjectEntry>> f 
                                    = commandExecutor.readAsync(client, name, new MapScanCodec(codec), RedisCommands.HSCAN, name, startPos);
            return get(f);
        }
        RFuture<MapScanResult<ScanObjectEntry, ScanObjectEntry>> f 
                                    = commandExecutor.readAsync(client, name, new MapScanCodec(codec), RedisCommands.HSCAN, name, startPos, "MATCH", pattern);
        return get(f);
    }

    @Override
    public V addAndGet(K key, Number value) {
        return get(addAndGetAsync(key, value));
    }

    @Override
    public RFuture<V> addAndGetAsync(final K key, Number value) {
        checkKey(key);
        checkValue(value);
        
        final RFuture<V> future = addAndGetOperationAsync(key, value);
        if (hasNoWriter()) {
            return future;
        }

        MapWriterTask<V> listener = new MapWriterTask<V>() {
            @Override
            public void execute() {
                options.getWriter().write(key, future.getNow());
            }
        };
        
        return mapWriterFuture(future, listener);
    }

    protected RFuture<V> addAndGetOperationAsync(K key, Number value) {
        ByteBuf keyState = encodeMapKey(key);
        RFuture<V> future = commandExecutor.writeAsync(getName(key), StringCodec.INSTANCE,
                new RedisCommand<Object>("HINCRBYFLOAT", new NumberConvertor(value.getClass())),
                getName(key), keyState, new BigDecimal(value.toString()).toPlainString());
        return future;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof Map))
            return false;
        Map<?,?> m = (Map<?,?>) o;
        if (m.size() != size())
            return false;

        try {
            Iterator<Entry<K,V>> i = entrySet().iterator();
            while (i.hasNext()) {
                Entry<K,V> e = i.next();
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Iterator<Entry<K,V>> i = entrySet().iterator();
        while (i.hasNext())
            h += i.next().hashCode();
        return h;
    }

    protected Iterator<K> keyIterator(String pattern) {
        return new RedissonMapIterator<K, V, K>(RedissonMap.this, pattern) {
            @Override
            protected K getValue(java.util.Map.Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (K) entry.getKey().getObj();
            }
        };
    }
    
    class KeySet extends AbstractSet<K> {

        private final String pattern;
        
        public KeySet(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public Iterator<K> iterator() {
            return keyIterator(pattern);
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMap.this.containsKey(o);
        }

        @Override
        public boolean remove(Object o) {
            return RedissonMap.this.fastRemove((K)o) == 1;
        }

        @Override
        public int size() {
            if (pattern != null) {
                int size = 0;
                for (K val : this) {
                    size++;
                }
                return size;
            }
            return RedissonMap.this.size();
        }

        @Override
        public void clear() {
            RedissonMap.this.clear();
        }

    }

    protected Iterator<V> valueIterator(String pattern) {
        return new RedissonMapIterator<K, V, V>(RedissonMap.this, pattern) {
            @Override
            protected V getValue(java.util.Map.Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (V) entry.getValue().getObj();
            }
        };
    }

    final class Values extends AbstractCollection<V> {

        private final String keyPattern;
        
        public Values(String keyPattern) {
            this.keyPattern = keyPattern;
        }

        @Override
        public Iterator<V> iterator() {
            return valueIterator(keyPattern);
        }

        @Override
        public boolean contains(Object o) {
            return RedissonMap.this.containsValue(o);
        }

        @Override
        public int size() {
            if (keyPattern != null) {
                int size = 0;
                for (V val : this) {
                    size++;
                }
                return size;
            }

            return RedissonMap.this.size();
        }

        @Override
        public void clear() {
            RedissonMap.this.clear();
        }

    }

    protected Iterator<Map.Entry<K,V>> entryIterator(String pattern) {
        return new RedissonMapIterator<K, V, Map.Entry<K, V>>(RedissonMap.this, pattern);
    }

    private void loadValue(final K key, final RPromise<V> result, final boolean replaceValue) {
        final RLock lock = getLock(key);
        final long threadId = Thread.currentThread().getId();
        lock.lockAsync(threadId).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    lock.unlockAsync(threadId);
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (replaceValue) {
                    loadValue(key, result, lock, threadId);
                    return;
                }
                
                getOperationAsync(key).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> valueFuture) throws Exception {
                        if (!valueFuture.isSuccess()) {
                            lock.unlockAsync(threadId);
                            result.tryFailure(valueFuture.cause());
                            return;
                        }
                        
                        if (valueFuture.getNow() != null) {
                            unlock(result, lock, threadId, valueFuture.getNow());
                            return;
                        }
                        
                        loadValue(key, result, lock, threadId);
                    }

                });
                
            }
        });
    }
    
    private void loadValue(final K key, final RPromise<V> result, final RLock lock,
            final long threadId) {
        commandExecutor.getConnectionManager().getExecutor().execute(new Runnable() {
            @Override
            public void run() {
                final V value = options.getLoader().load(key);
                if (value == null) {
                    unlock(result, lock, threadId, value);
                    return;
                }
                    
                externalPutAsync(key, value).addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess()) {
                            lock.unlockAsync(threadId);
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        unlock(result, lock, threadId, value);
                    }
                });
            }
        });
    }

    private void unlock(final RPromise<V> result, RLock lock, long threadId,
            final V value) {
        lock.unlockAsync(threadId).addListener(new FutureListener<Void>() {
            @Override
            public void operationComplete(Future<Void> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                result.trySuccess(value);
            }
        });
    }

    final class EntrySet extends AbstractSet<Map.Entry<K,V>> {

        private final String keyPattern;
        
        public EntrySet(String keyPattern) {
            this.keyPattern = keyPattern;
        }

        public final Iterator<Map.Entry<K,V>> iterator() {
            return entryIterator(keyPattern);
        }

        public final boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?,?> e = (Map.Entry<?,?>) o;
            Object key = e.getKey();
            V value = get(key);
            return value != null && value.equals(e);
        }

        public final boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>) o;
                Object key = e.getKey();
                Object value = e.getValue();
                return RedissonMap.this.remove(key, value);
            }
            return false;
        }

        public final int size() {
            if (keyPattern != null) {
                int size = 0;
                for (Entry val : this) {
                    size++;
                }
                return size;
            }
            
            return RedissonMap.this.size();
        }

        public final void clear() {
            RedissonMap.this.clear();
        }

    }

}
