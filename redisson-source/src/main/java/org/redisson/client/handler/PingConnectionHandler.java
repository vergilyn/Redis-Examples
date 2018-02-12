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
package org.redisson.client.handler;

import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.redisson.api.RFuture;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.protocol.RedisCommands;

/**
 * 
 * @author Nikita Koksharov
 *
 */
@Sharable
public class PingConnectionHandler extends ChannelInboundHandlerAdapter {

    private final RedisClientConfig config;

    public PingConnectionHandler(RedisClientConfig config) {
        this.config = config;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        sendPing(ctx);
        ctx.fireChannelActive();
    }

    protected void sendPing(final ChannelHandlerContext ctx) {
        RedisConnection connection = RedisConnection.getFrom(ctx.channel());
        final RFuture<Object> future = connection.async(RedisCommands.PING);
        
        config.getTimer().newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                if (future.cancel(false)) {
                    ctx.channel().close();
                } else {
                    sendPing(ctx);
                }
            }
        }, config.getPingConnectionInterval(), TimeUnit.MILLISECONDS);
    }


}
