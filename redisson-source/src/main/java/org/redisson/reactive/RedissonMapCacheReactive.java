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
package org.redisson.reactive;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.RedissonMapCache;
import org.redisson.api.MapOptions;
import org.redisson.api.RFuture;
import org.redisson.api.RMapCacheAsync;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.client.protocol.decoder.ScanObjectEntry;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.eviction.EvictionScheduler;
import reactor.fn.BiFunction;
import reactor.fn.Function;
import reactor.fn.Supplier;
import reactor.rx.Streams;

/**
 * <p>Map-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} or {@link #putIfAbsent(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.</p>
 *
 * <p>Current redis implementation doesnt have map entry eviction functionality.
 * Thus entries are checked for TTL expiration during any key/value/entry read operation.
 * If key/value/entry expired then it doesn't returns and clean task runs asynchronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.eviction.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonMapReactive}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonMapCacheReactive<K, V> extends RedissonExpirableReactive implements RMapCacheReactive<K, V>, MapReactive<K, V> {

    private final RMapCacheAsync<K, V> mapCache;

    public RedissonMapCacheReactive(EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name, MapOptions<K, V> options) {
        super(commandExecutor, name);
        this.mapCache = new RedissonMapCache<K, V>(evictionScheduler, commandExecutor, name, null, options);
    }

    public RedissonMapCacheReactive(EvictionScheduler evictionScheduler, Codec codec, CommandReactiveExecutor commandExecutor, String name, MapOptions<K, V> options) {
        super(codec, commandExecutor, name);
        this.mapCache = new RedissonMapCache<K, V>(codec, evictionScheduler, commandExecutor, name, null, options);
    }

    @Override
    public Publisher<Boolean> containsKey(final Object key) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.containsKeyAsync(key);
            }
        });
    }

    @Override
    public Publisher<Boolean> containsValue(final Object value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.containsValueAsync(value);
            }
        });
    }

    @Override
    public Publisher<Map<K, V>> getAll(final Set<K> keys) {
        return reactive(new Supplier<RFuture<Map<K, V>>>() {
            @Override
            public RFuture<Map<K, V>> get() {
                return mapCache.getAllAsync(keys);
            }
        });
    }

    @Override
    public Publisher<V> putIfAbsent(final K key, final V value, final long ttl, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putIfAbsentAsync(key, value, ttl, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> remove(final Object key, final Object value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.removeAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<V> get(final K key) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.getAsync(key);
            }
        });
    }

    @Override
    public Publisher<V> put(final K key, final V value, final long ttl, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putAsync(key, value, ttl, unit);
            }
        });
    }

    @Override
    public Publisher<V> remove(final K key) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.removeAsync(key);
            }
        });
    }

    @Override
    public Publisher<Long> fastRemove(final K ... keys) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return mapCache.fastRemoveAsync(keys);
            }
        });
    }

    @Override
    public Publisher<MapScanResult<ScanObjectEntry, ScanObjectEntry>> scanIteratorReactive(final RedisClient client, final long startPos) {
        return reactive(new Supplier<RFuture<MapScanResult<ScanObjectEntry, ScanObjectEntry>>>() {
            @Override
            public RFuture<MapScanResult<ScanObjectEntry, ScanObjectEntry>> get() {
                return ((RedissonMapCache<K, V>)mapCache).scanIteratorAsync(getName(), client, startPos, null);
            }
        });
    }

    @Override
    public Publisher<Boolean> delete() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.deleteAsync();
            }
        });
    }

    @Override
    public Publisher<Boolean> expire(final long timeToLive, final TimeUnit timeUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.expireAsync(timeToLive, timeUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> expireAt(final long timestamp) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.expireAtAsync(timestamp);
            }
        });
    }

    @Override
    public Publisher<Boolean> clearExpire() {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.clearExpireAsync();
            }
        });
    }

    @Override
    public Publisher<Void> putAll(final Map<? extends K, ? extends V> map) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return mapCache.putAllAsync(map);
            }
        });
    }

    @Override
    public Publisher<V> addAndGet(final K key, final Number delta) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.addAndGetAsync(key, delta);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPut(final K key, final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.fastPutAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<V> put(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<V> replace(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.replaceAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Boolean> replace(final K key, final V oldValue, final V newValue) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.replaceAsync(key, oldValue, newValue);
            }
        });
    }

    @Override
    public Publisher<V> putIfAbsent(final K key, final V value) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putIfAbsentAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Map.Entry<K, V>> entryIterator() {
        return new RedissonMapReactiveIterator<K, V, Map.Entry<K, V>>(this).stream();
    }

    @Override
    public Publisher<V> valueIterator() {
        return new RedissonMapReactiveIterator<K, V, V>(this) {
            @Override
            V getValue(Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (V) entry.getValue().getObj();
            }
        }.stream();
    }

    @Override
    public Publisher<K> keyIterator() {
        return new RedissonMapReactiveIterator<K, V, K>(this) {
            @Override
            K getValue(Entry<ScanObjectEntry, ScanObjectEntry> entry) {
                return (K) entry.getKey().getObj();
            }
        }.stream();
    }

    @Override
    public Publisher<Integer> size() {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return mapCache.sizeAsync();
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (o instanceof Map) {
            final Map<?,?> m = (Map<?,?>) o;
            if (m.size() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        } else if (o instanceof RMapReactive) {
            final RMapReactive<Object, Object> m = (RMapReactive<Object, Object>) o;
            if (Streams.create(m.size()).next().poll() != Streams.create(size()).next().poll()) {
                return false;
            }

            return Streams.create(entryIterator()).map(mapFunction(m)).reduce(true, booleanAnd()).next().poll();
        }

        return true;
    }

    private BiFunction<Boolean, Boolean, Boolean> booleanAnd() {
        return new BiFunction<Boolean, Boolean, Boolean>() {

            @Override
            public Boolean apply(Boolean t, Boolean u) {
                return t & u;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final Map<?, ?> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                K key = e.getKey();
                V value = e.getValue();
                if (value == null) {
                    if (!(m.get(key)==null && m.containsKey(key)))
                        return false;
                } else {
                    if (!value.equals(m.get(key)))
                        return false;
                }
                return true;
            }
        };
    }

    private Function<Entry<K, V>, Boolean> mapFunction(final RMapReactive<Object, Object> m) {
        return new Function<Map.Entry<K, V>, Boolean>() {
            @Override
            public Boolean apply(Entry<K, V> e) {
                Object key = e.getKey();
                Object value = e.getValue();
                if (value == null) {
                    if (!(Streams.create(m.get(key)).next().poll() ==null && Streams.create(m.containsKey(key)).next().poll()))
                        return false;
                } else {
                    if (!value.equals(Streams.create(m.get(key)).next().poll()))
                        return false;
                }
                return true;
            }
        };
    }

    @Override
    public int hashCode() {
        return Streams.create(entryIterator()).map(new Function<Map.Entry<K, V>, Integer>() {
            @Override
            public Integer apply(Entry<K, V> t) {
                return t.hashCode();
            }
        }).reduce(0, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer apply(Integer t, Integer u) {
                return t + u;
            }
        }).next().poll();
    }

    @Override
    public Publisher<Void> loadAll(final boolean replaceExistingValues, final int parallelism) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return mapCache.loadAllAsync(replaceExistingValues, parallelism);
            }
        });
    }

    @Override
    public Publisher<Void> loadAll(final Set<? extends K> keys, final boolean replaceExistingValues, final int parallelism) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return mapCache.loadAllAsync(keys, replaceExistingValues, parallelism);
            }
        });
    }

    @Override
    public Publisher<Integer> valueSize(final K key) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return mapCache.valueSizeAsync(key);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPutIfAbsent(final K key, final V value) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.fastPutIfAbsentAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Set<K>> readAllKeySet() {
        return reactive(new Supplier<RFuture<Set<K>>>() {
            @Override
            public RFuture<Set<K>> get() {
                return mapCache.readAllKeySetAsync();
            }
        });
    }

    @Override
    public Publisher<Collection<V>> readAllValues() {
        return reactive(new Supplier<RFuture<Collection<V>>>() {
            @Override
            public RFuture<Collection<V>> get() {
                return mapCache.readAllValuesAsync();
            }
        });
    }

    @Override
    public Publisher<Set<Entry<K, V>>> readAllEntrySet() {
        return reactive(new Supplier<RFuture<Set<Entry<K, V>>>>() {
            @Override
            public RFuture<Set<Entry<K, V>>> get() {
                return mapCache.readAllEntrySetAsync();
            }
        });
    }

    @Override
    public Publisher<Map<K, V>> readAllMap() {
        return reactive(new Supplier<RFuture<Map<K, V>>>() {
            @Override
            public RFuture<Map<K, V>> get() {
                return mapCache.readAllMapAsync();
            }
        });
    }

    @Override
    public Publisher<V> putIfAbsent(final K key, final V value, final long ttl, final TimeUnit ttlUnit, final long maxIdleTime,
            final TimeUnit maxIdleUnit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
            }
        });
    }

    @Override
    public Publisher<V> put(final K key, final V value, final long ttl, final TimeUnit ttlUnit, final long maxIdleTime, final TimeUnit maxIdleUnit) {
        return reactive(new Supplier<RFuture<V>>() {
            @Override
            public RFuture<V> get() {
                return mapCache.putAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPut(final K key, final V value, final long ttl, final TimeUnit unit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.fastPutAsync(key, value, ttl, unit);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPut(final K key, final V value, final long ttl, final TimeUnit ttlUnit, final long maxIdleTime,
            final TimeUnit maxIdleUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.fastPutAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
            }
        });
    }

    @Override
    public Publisher<Boolean> fastPutIfAbsent(final K key, final V value, final long ttl, final TimeUnit ttlUnit, final long maxIdleTime,
            final TimeUnit maxIdleUnit) {
        return reactive(new Supplier<RFuture<Boolean>>() {
            @Override
            public RFuture<Boolean> get() {
                return mapCache.fastPutIfAbsentAsync(key, value, ttl, ttlUnit, maxIdleTime, maxIdleUnit);
            }
        });
    }

}
