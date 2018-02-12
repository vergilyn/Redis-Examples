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
package org.redisson.cache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.redisson.client.codec.Codec;
import org.redisson.client.handler.State;
import org.redisson.client.protocol.Decoder;
import org.redisson.client.protocol.Encoder;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class LocalCachedMessageCodec implements Codec {

    private final Decoder<Object> decoder = new Decoder<Object>() {
        @Override
        public Object decode(ByteBuf buf, State state) throws IOException {
            byte type = buf.readByte();
            if (type == 0x0) {
                return new LocalCachedMapClear();
            }
            
            if (type == 0x1) {
                byte[] excludedId = new byte[16];
                buf.readBytes(excludedId);
                int hashesCount = buf.readInt();
                byte[][] hashes = new byte[hashesCount][];
                for (int i = 0; i < hashesCount; i++) {
                    byte[] keyHash = new byte[16];
                    buf.readBytes(keyHash);
                    hashes[i] = keyHash;
                }
                return new LocalCachedMapInvalidate(excludedId, hashes);
            }
            
            if (type == 0x2) {
                List<LocalCachedMapUpdate.Entry> entries = new ArrayList<LocalCachedMapUpdate.Entry>();
                while (true) {
                    int keyLen = buf.readInt();
                    byte[] key = new byte[keyLen];
                    buf.readBytes(key);
                    int valueLen = buf.readInt();
                    byte[] value = new byte[valueLen];
                    buf.readBytes(value);
                    entries.add(new LocalCachedMapUpdate.Entry(key, value));
                    
                    if (!buf.isReadable()) {
                        break;
                    }
                }
                return new LocalCachedMapUpdate(entries);
            }

            throw new IllegalArgumentException("Can't parse packet");
        }
    };
    
    private final Encoder encoder = new Encoder() {

        @Override
        public ByteBuf encode(Object in) throws IOException {
            if (in instanceof LocalCachedMapClear) {
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer(1);
                result.writeByte(0x0);
                return result;
            }
            if (in instanceof LocalCachedMapInvalidate) {
                LocalCachedMapInvalidate li = (LocalCachedMapInvalidate) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer();
                result.writeByte(0x1);
                result.writeBytes(li.getExcludedId());
                result.writeInt(li.getKeyHashes().length);
                for (int i = 0; i < li.getKeyHashes().length; i++) {
                    result.writeBytes(li.getKeyHashes()[i]);
                }
                return result;
            }
            
            if (in instanceof LocalCachedMapUpdate) {
                LocalCachedMapUpdate li = (LocalCachedMapUpdate) in;
                ByteBuf result = ByteBufAllocator.DEFAULT.buffer(256);
                result.writeByte(0x2);

                for (LocalCachedMapUpdate.Entry e : li.getEntries()) {
                    result.writeInt(e.getKey().length);
                    result.writeBytes(e.getKey());
                    result.writeInt(e.getValue().length);
                    result.writeBytes(e.getValue());
                }
                return result;
            }

            throw new IllegalArgumentException("Can't encode packet " + in);
        }
    };


    public LocalCachedMessageCodec() {
    }

    @Override
    public Decoder<Object> getMapValueDecoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Encoder getMapValueEncoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Decoder<Object> getMapKeyDecoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Encoder getMapKeyEncoder() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Decoder<Object> getValueDecoder() {
        return decoder;
    }

    @Override
    public Encoder getValueEncoder() {
        return encoder;
    }

}
