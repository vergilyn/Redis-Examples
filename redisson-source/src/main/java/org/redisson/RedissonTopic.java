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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import org.redisson.api.RFuture;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.StatusListener;
import org.redisson.client.RedisPubSubListener;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.PubSubConnectionEntry;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonObjectFactory;
import org.redisson.misc.RedissonPromise;
import org.redisson.pubsub.AsyncSemaphore;

/**
 * Distributed topic implementation. Messages are delivered to all message listeners across Redis cluster.
 *
 * @author Nikita Koksharov
 *
 * @param <M> message
 */
public class RedissonTopic<M> implements RTopic<M> {

    final CommandAsyncExecutor commandExecutor;
    private final String name;
    private final Codec codec;

    public RedissonTopic(CommandAsyncExecutor commandExecutor, String name) {
        this(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public RedissonTopic(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.codec = codec;
    }

    public List<String> getChannelNames() {
        return Collections.singletonList(name);
    }

    @Override
    public long publish(M message) {
        return commandExecutor.get(publishAsync(message));
    }

    @Override
    public RFuture<Long> publishAsync(M message) {
        return commandExecutor.writeAsync(name, codec, RedisCommands.PUBLISH, name, encode(message));
    }

    protected ByteBuf encode(Object value) {
        if (commandExecutor.isRedissonReferenceSupportEnabled()) {
            RedissonReference reference = RedissonObjectFactory.toReference(commandExecutor.getConnectionManager().getCfg(), value);
            if (reference != null) {
                value = reference;
            }
        }
        
        try {
            return codec.getValueEncoder().encode(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    @Override
    public int addListener(StatusListener listener) {
        return addListener(new PubSubStatusListener<Object>(listener, name));
    };

    @Override
    public int addListener(MessageListener<M> listener) {
        PubSubMessageListener<M> pubSubListener = new PubSubMessageListener<M>(listener, name);
        return addListener(pubSubListener);
    }

    private int addListener(RedisPubSubListener<?> pubSubListener) {
        RFuture<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().subscribe(codec, name, pubSubListener);
        commandExecutor.syncSubscription(future);
        return System.identityHashCode(pubSubListener);
    }
    
    public RFuture<Integer> addListenerAsync(final RedisPubSubListener<?> pubSubListener) {
        RFuture<PubSubConnectionEntry> future = commandExecutor.getConnectionManager().subscribe(codec, name, pubSubListener);
        final RPromise<Integer> result = new RedissonPromise<Integer>();
        future.addListener(new FutureListener<PubSubConnectionEntry>() {
            @Override
            public void operationComplete(Future<PubSubConnectionEntry> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                result.trySuccess(System.identityHashCode(pubSubListener));
            }
        });
        return result;
    }

    @Override
    public void removeAllListeners() {
        AsyncSemaphore semaphore = commandExecutor.getConnectionManager().getSemaphore(name);
        semaphore.acquireUninterruptibly();
        
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getPubSubEntry(name);
        if (entry == null) {
            semaphore.release();
            return;
        }

        entry.removeAllListeners(name);
        if (!entry.hasListeners(name)) {
            commandExecutor.getConnectionManager().unsubscribe(name, semaphore);
        } else {
            semaphore.release();
        }
    }
    
    @Override
    public void removeListener(MessageListener<?> listener) {
        AsyncSemaphore semaphore = commandExecutor.getConnectionManager().getSemaphore(name);
        semaphore.acquireUninterruptibly();
        
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getPubSubEntry(name);
        if (entry == null) {
            semaphore.release();
            return;
        }

        entry.removeListener(name, listener);
        if (!entry.hasListeners(name)) {
            commandExecutor.getConnectionManager().unsubscribe(name, semaphore);
        } else {
            semaphore.release();
        }

    }
    
    @Override
    public void removeListener(int listenerId) {
        AsyncSemaphore semaphore = commandExecutor.getConnectionManager().getSemaphore(name);
        semaphore.acquireUninterruptibly();
        
        PubSubConnectionEntry entry = commandExecutor.getConnectionManager().getPubSubEntry(name);
        if (entry == null) {
            semaphore.release();
            return;
        }

        entry.removeListener(name, listenerId);
        if (!entry.hasListeners(name)) {
            commandExecutor.getConnectionManager().unsubscribe(name, semaphore);
        } else {
            semaphore.release();
        }
    }

}
