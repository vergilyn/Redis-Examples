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

import java.util.Iterator;
import java.util.Set;

import org.redisson.api.mapreduce.RCollectionMapReduce;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public interface RSet<V> extends Set<V>, RExpirable, RSetAsync<V>, RSortable<Set<V>> {

    /**
     * Returns values iterator matches <code>pattern</code>. 
     * 
     * @param pattern for values
     * @return iterator
     */
    Iterator<V> iterator(String pattern);
    
    /**
     * Returns <code>RMapReduce</code> object associated with this object
     * 
     * @param <KOut> output key
     * @param <VOut> output value
     * @return MapReduce instance
     */
    <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce();
    
    /**
     * Removes and returns random elements from set
     * 
     * @param amount of random values
     * @return random values
     */
    Set<V> removeRandom(int amount);
    
    /**
     * Removes and returns random element from set
     *
     * @return value
     */
    V removeRandom();

    /**
     * Returns random element from set
     *
     * @return value
     */
    V random();

    /**
     * Move a member from this set to the given destination set in.
     *
     * @param destination the destination set
     * @param member the member to move
     * @return true if the element is moved, false if the element is not a
     * member of this set or no operation was performed
     */
    boolean move(String destination, V member);

    /**
     * Read all elements at once
     *
     * @return values
     */
    Set<V> readAll();

    /**
     * Union sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of union
     */
    int union(String... names);

    /**
     * Union sets specified by name with current set
     * without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Set<V> readUnion(String... names);

    /**
     * Diff sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return values
     */
    int diff(String... names);

    /**
     * Diff sets specified by name with current set.
     * Without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */

    Set<V> readDiff(String... names);
    /**
     * Intersection sets specified by name and write to current set.
     * If current set already exists, it is overwritten.
     *
     * @param names - name of sets
     * @return size of intersection
     */
    int intersection(String... names);

    /**
     * Intersection sets specified by name with current set
     * without current set state change.
     * 
     * @param names - name of sets
     * @return values
     */
    Set<V> readIntersection(String... names);

}
