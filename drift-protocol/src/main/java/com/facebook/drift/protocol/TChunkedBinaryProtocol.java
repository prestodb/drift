/*
 * Copyright (C) 2012 ${project.organization.name}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.drift.protocol;

import com.facebook.drift.TException;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Consumer;

public class TChunkedBinaryProtocol
        extends TBinaryProtocol
{
    private static final int DEFAULT_CHUNK_SIZE = 4096;
    private static final int MAX_BUFFER_SIZE = 100 * 1024 * 1024;
    private final TTransport transport;

    private TChunkedBinaryProtocol(TTransport transport)
    {
        super(transport);
        this.transport = transport;
    }

    public static TChunkedBinaryProtocol forRead(List<ByteBuf> buffers)
    {
        return new TChunkedBinaryProtocol(new TChunkedTransport(buffers));
    }

    public static TChunkedBinaryProtocol forWrite(ByteBufAllocator allocator)
    {
        return forWrite(allocator, DEFAULT_CHUNK_SIZE);
    }

    public static TChunkedBinaryProtocol forWrite(ByteBufAllocator allocator, int chunkSize)
    {
        return new TChunkedBinaryProtocol(new TChunkedTransport(allocator, chunkSize));
    }

    public List<ByteBuf> getBuffers()
            throws TException
    {
        if (!(this.transport instanceof TChunkedTransport)) {
            throw new TException("Transport is not a TChunkedTransport");
        }
        return ((TChunkedTransport) this.transport).getBuffers();
    }

    public static <T> void serialize(
            ByteBufAllocator allocator,
            T value,
            ObjectWriter<T> writer,
            Consumer<List<ByteBuf>> consumer)
            throws Exception
    {
        serialize(allocator, DEFAULT_CHUNK_SIZE, value, writer, consumer);
    }

    public static <T> void serialize(
            ByteBufAllocator allocator,
            int chunkSize,
            T value,
            ObjectWriter<T> writer,
            Consumer<List<ByteBuffer>> consumer)
            throws Exception
    {
        if (chunkSize <= 0) {
            chunkSize = DEFAULT_CHUNK_SIZE;
        }

        TChunkedTransport transport = new TChunkedTransport(allocator, chunkSize);

        try {
            TChunkedBinaryProtocol protocol = new TChunkedBinaryProtocol(transport);
            writer.write(value, protocol);

            List<ByteBuf> buffers = protocol.getBuffers();

            consumer.accept(buffers);
        }
        catch (Exception e) {
            transport.release();
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(List<ByteBuf> buffers, ObjectReader<T> reader)
            throws Exception
    {
        TChunkedTransport transport = new TChunkedTransport(buffers);
        TChunkedBinaryProtocol protocol = new TChunkedBinaryProtocol(transport);

        return reader.read(protocol);
    }

    @Override
    public void writeBinary(List<ByteBuf> buffers)
            throws TException
    {
        if (!(this.transport instanceof TChunkedTransport)) {
            throw new TException("Transport is not a TChunkedTransport");
        }

        int totalSize = 0;
        for (ByteBuf buffer : buffers) {
            totalSize += buffer.readableBytes();
            if (totalSize > MAX_BUFFER_SIZE) {
                throw new TException("Too many bytes written");
            }
        }

        writeI32(totalSize);
        for (ByteBuf buffer : buffers) {
            transport.write(buffer);
        }
    }

    @Override
    public List<ByteBuf> readBinaryAsByteBufList()
            throws TException
    {
        int length = readI32();
        if (length == 0) {
            return ImmutableList.of();
        }
        if (length > MAX_BUFFER_SIZE) {
            throw new TException("Buffer length exceed maximum allowed length: " + MAX_BUFFER_SIZE);
        }

        TChunkedTransport chunkedTransport = (TChunkedTransport) transport;
        return chunkedTransport.readBinaryAsList(length);
    }

    public interface ObjectWriter<T>
    {
        void write(T value, TProtocol protocol)
                throws Exception;
    }

    public interface ObjectReader<T>
    {
        T read(TProtocol protocol)
                throws Exception;
    }
}
