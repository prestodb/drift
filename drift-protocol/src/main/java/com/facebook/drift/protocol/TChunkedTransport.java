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

import com.facebook.drift.protocol.bytebuffer.ChunkedBufferReader;
import com.facebook.drift.protocol.bytebuffer.ChunkedBufferWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.List;

import static java.util.Objects.requireNonNull;

public class TChunkedTransport
        implements TTransport
{
    private final ChunkedBufferReader reader;
    private final ChunkedBufferWriter writer;

    public TChunkedTransport(List<ByteBuf> buffers)
    {
        this.reader = new ChunkedBufferReader(buffers);
        this.writer = null;
    }

    public TChunkedTransport(ByteBufAllocator allocator, int chunkSize)
    {
        this.reader = null;
        this.writer = new ChunkedBufferWriter(allocator, chunkSize);
    }

    @Override
    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        if (reader == null) {
            throw new TTransportException("reader is null");
        }

        reader.read(buf, off, len);
    }

    @Override
    public void write(byte[] buf, int off, int len)
            throws TTransportException
    {
        if (writer == null) {
            throw new TTransportException("writer is null");
        }

        writer.writeBytes(buf, off, len);
    }

    @Override
    public void write(ByteBuf buffer)
            throws TTransportException
    {
        if (writer == null) {
            throw new TTransportException("writer is null");
        }

        requireNonNull(buffer, "buffer is null");

        // If the buffer is small, just copy it
        if (buffer.readableBytes() <= 128) {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            writer.writeBytes(bytes);
            return;
        }

        ByteBuf retainedBuf = buffer.retain();
        writer.getBuffers().add(retainedBuf);
    }

    public List<ByteBuf> readBinaryAsList(int length)
            throws TTransportException
    {
        if (reader == null) {
            throw new TTransportException("reader is null");
        }
        return reader.readBinaryAsList(length);
    }

    public List<ByteBuf> getBuffers()
    {
        requireNonNull(writer, "writer is null");
        return writer.getBuffers();
    }

    public void release()
    {
        if (writer != null) {
            writer.release();
        }
    }
}
