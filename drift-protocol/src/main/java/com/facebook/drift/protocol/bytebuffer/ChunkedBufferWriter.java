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
package com.facebook.drift.protocol.bytebuffer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class ChunkedBufferWriter
{
    private final ByteBufAllocator allocator;
    private final List<ByteBuf> buffers = new ArrayList<>();
    private final int chunkSize;
    private ByteBuf currentBuffer;

    public ChunkedBufferWriter(ByteBufAllocator allocator, int chunkSize)
    {
        this.allocator = requireNonNull(allocator, "allocator is null");
        this.chunkSize = chunkSize;
        allocateNewBuffer();
    }

    public List<ByteBuf> getBuffers()
    {
        return buffers;
    }

    public void writeByte(byte value)
    {
        ensureWritable(1);
        currentBuffer.writeByte(value);
    }

    public void writeShort(short value)
    {
        ensureWritable(2);
        if (currentBuffer.writableBytes() >= 2) {
            currentBuffer.writeShort(value);
        }
        else {
            writeByte((byte) (value >> 8));
            writeByte((byte) value);
        }
    }

    public void writeInt(int value)
    {
        ensureWritable(4);
        if (currentBuffer.writableBytes() >= 4) {
            currentBuffer.writeInt(value);
        }
        else {
            writeShort((short) (value >> 16));
            writeShort((short) value);
        }
    }

    public void writeLong(long value)
    {
        ensureWritable(8);
        if (currentBuffer.writableBytes() >= 8) {
            currentBuffer.writeLong(value);
        }
        else {
            writeInt((int) (value >> 32));
            writeInt((int) value);
        }
    }

    public void writeBytes(byte[] bytes)
    {
        writeBytes(bytes, 0, bytes.length);
    }

    public void writeBytes(byte[] bytes, int offset, int length)
    {
        int remaining = length;
        int currentOffset = offset;

        // TODO: potential improvement by writing the reset of the first buffer first, then batch write.
        while (remaining > 0) {
            ensureWritable(1);
            int bytesToWrite = Math.min(remaining, currentBuffer.writableBytes());
            currentBuffer.writeBytes(bytes, currentOffset, bytesToWrite);

            remaining -= bytesToWrite;
            currentOffset += bytesToWrite;
        }
    }

    private void ensureWritable(int bytesNeeded)
    {
        if (currentBuffer.writableBytes() < bytesNeeded) {
            allocateNewBuffer();
        }
    }

    private void allocateNewBuffer()
    {
        currentBuffer = allocator.heapBuffer(chunkSize);
        buffers.add(currentBuffer);
    }

    public void release()
    {
        for (ByteBuf buffer : buffers) {
            buffer.release();
        }
        buffers.clear();
        currentBuffer = null;
    }
}
