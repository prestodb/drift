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

import com.facebook.drift.protocol.TTransportException;
import com.google.common.collect.ImmutableList;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class ChunkedBufferReader
{
    private final List<ByteBuf> buffers;
    private int currentBufferIndex;
    private ByteBuf currentBuffer;

    public ChunkedBufferReader(List<ByteBuf> buffers)
    {
        this.buffers = requireNonNull(buffers, "buffers is null");
        this.currentBufferIndex = 0;
        this.currentBuffer = buffers.isEmpty() ? null : buffers.get(0);
    }

    public ByteBuf getCurrentBuffer()
    {
        return currentBuffer;
    }

    public boolean moveToNextBuffer()
    {
        if (currentBufferIndex + 1 >= buffers.size()) {
            return false;
        }
        currentBufferIndex += 1;
        currentBuffer = buffers.get(currentBufferIndex);
        return true;
    }

    public byte readByte()
            throws TTransportException
    {
        ensureReadable(1);
        return currentBuffer.readByte();
    }

    public short readShort()
            throws TTransportException
    {
        ensureReadable(2);

        if (currentBuffer.readableBytes() >= 2) {
            return currentBuffer.readShort();
        }
        byte b1 = readByte();
        byte b2 = readByte();
        return (short) ((b1 << 8) | (b2 & 0xff));
    }

    public int readInt()
            throws TTransportException
    {
        ensureReadable(4);

        if (currentBuffer.readableBytes() >= 4) {
            return currentBuffer.readInt();
        }

        byte b1 = readByte();
        byte b2 = readByte();
        byte b3 = readByte();
        byte b4 = readByte();

        return ((b1 << 24) | (b2 << 16) | (b3 << 8) | b4);
    }

    public long readLong()
            throws TTransportException
    {
        ensureReadable(8);
        if (currentBuffer.readableBytes() >= 8) {
            return currentBuffer.readLong();
        }

        int hi = readInt();
        int lo = readInt();

        return ((long) hi << 32) | lo;
    }

    public List<ByteBuf> readBinaryAsList(int length)
            throws TTransportException
    {
        ensureReadable(length);

        requireNonNull(currentBuffer, "currentBuffer is null");

        if (currentBuffer.readableBytes() >= length) {
            return ImmutableList.of(currentBuffer.readRetainedSlice(length));
        }

        // Read from multiple buffers
        List<ByteBuf> result = new ArrayList<>();
        int bytesRemaining = length;

        int bytesToRead = Math.min(bytesRemaining, currentBuffer.readableBytes());
        result.add(currentBuffer.readRetainedSlice(bytesToRead));
        bytesRemaining -= bytesToRead;

        while (bytesRemaining > 0) {
            if (!moveToNextBuffer()) {
                result.forEach(ByteBuf::release);
                throw new TTransportException("Unexpected end of buffers while reading");
            }

            bytesToRead = Math.min(bytesRemaining, currentBuffer.readableBytes());
            result.add(currentBuffer.readRetainedSlice(bytesToRead));
            bytesRemaining -= bytesToRead;
        }
        return result;
    }

    private void ensureReadable(int bytesNeeded)
            throws TTransportException
    {
        if (buffers == null || buffers.isEmpty()) {
            throw new TTransportException("Buffer is null or empty");
        }

        if (currentBuffer == null) {
            currentBufferIndex = 0;
            currentBuffer = buffers.get(currentBufferIndex);

            while (currentBuffer.readableBytes() == 0) {
                if (++currentBufferIndex == buffers.size()) {
                    throw new TTransportException("Not enough bytes available to read");
                }
                currentBuffer = buffers.get(currentBufferIndex);
            }
        }

        if (currentBuffer.readableBytes() >= bytesNeeded) {
            return;
        }

        if (bytesNeeded == 1) {
            while (currentBuffer.readableBytes() == 0) {
                if (++currentBufferIndex == buffers.size()) {
                    throw new TTransportException("Not enough bytes available to read");
                }
                currentBuffer = buffers.get(currentBufferIndex);
            }
            return;
        }

        // For multibyte primitives that span buffer boundaries
        int availableBytes = 0;
        for (int i = currentBufferIndex; i < buffers.size(); i++) {
            availableBytes += buffers.get(i).readableBytes();
            if (availableBytes >= bytesNeeded) {
                return;
            }
        }

        throw new TTransportException("Not enough bytes available to read. Need " + bytesNeeded + " bytes, but only found " + availableBytes + " bytes.");
    }

    public boolean hasRemaining()
    {
        for (int i = currentBufferIndex; i < buffers.size(); i++) {
            if (buffers.get(i).readableBytes() > 0) {
                return true;
            }
        }
        return false;
    }

    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        int bytesRemaining = len;
        int currentOffset = off;

        while (bytesRemaining > 0) {
            if (currentBuffer == null || !currentBuffer.isReadable()) {
                if (!moveToNextBuffer()) {
                    throw new TTransportException("Not enough bytes available");
                }
                continue;
            }

            int bytesToRead = Math.min(bytesRemaining, currentBuffer.readableBytes());
            currentBuffer.readBytes(buf, currentOffset, bytesToRead);

            bytesRemaining -= bytesToRead;
            currentOffset += bytesToRead;
        }
    }
}
