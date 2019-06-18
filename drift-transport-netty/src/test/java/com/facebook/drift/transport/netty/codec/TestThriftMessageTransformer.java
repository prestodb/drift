/*
 * Copyright (C) 2018 Facebook, Inc.
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
package com.facebook.drift.transport.netty.codec;

import com.facebook.drift.transport.netty.buffer.TestingPooledByteBufAllocator;
import com.google.common.base.Strings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Random;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestThriftMessageTransformer
{
    @Test
    public void testZlibRoundTrips()
            throws IOException
    {
        assertRoundTrip(new ZlibTransformer(512), "123");
        assertRoundTrip(new ZlibTransformer(1), Strings.repeat("a", 100000));
        assertRoundTrip(new ZlibTransformer(512), Strings.repeat("a", 100000));
        assertRoundTrip(new ZlibTransformer(1), randomString(100000));
        assertRoundTrip(new ZlibTransformer(512), randomString(100000));
        assertRoundTrip(new ZlibTransformer(200000), randomString(100000));
    }

    @Test
    public void testZlibAttemptPartialRead()
            throws IOException
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf transformed = null;
            ByteBuf untransformed = null;
            try {
                transformed = encode(allocator, new ZlibTransformer(2), Strings.repeat("a", 1000));
                // Failed to decode through normal untransform method, but shouldn't introduce memory leak.
                try {
                    untransformed = decode(allocator, new ZlibTransformer(2), transformed.retainedSlice(0, 10));
                    throw new Exception("not expected");
                }
                catch (Throwable t) {
                    assertFalse(t.getMessage().equals("not expected"));
                }
                // 10 byte will be enough to decode
                untransformed = tryDecode(allocator, new ZlibTransformer(2), transformed.retainedSlice(0, 10), 5);
                assertTrue(toString(untransformed).startsWith("a"));
                untransformed.release();
                // reduce size limit to allow read less
                untransformed = tryDecode(allocator, new ZlibTransformer(2), transformed.retainedSlice(0, 17), 1);
                assertTrue(toString(untransformed).startsWith("a"));
                untransformed.release();
                // Too short to be decoded.
                untransformed = tryDecode(allocator, new ZlibTransformer(2), transformed.retainedSlice(0, 1), 10);
                assertFalse(toString(untransformed).startsWith("a"));
            }
            finally {
                if (transformed != null) {
                    transformed.release();
                }
                if (untransformed != null) {
                    untransformed.release();
                }
            }
        }
    }

    private static void assertRoundTrip(ThriftMessageTransformer transformer, String input)
            throws IOException
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf inputBuffer = allocator.buffer();
            inputBuffer.writeBytes(input.getBytes());
            ByteBuf outputBuffer = null;
            try {
                ByteBuf transformed = transformer.transform(allocator, inputBuffer.retain());
                outputBuffer = transformer.untransform(allocator, transformed);
                assertEquals(input, toString(outputBuffer));
            }
            finally {
                inputBuffer.release();
                if (outputBuffer != null) {
                    outputBuffer.release();
                }
            }
        }
    }

    private static String randomString(int size)
    {
        byte[] array = new byte[size];
        new Random().nextBytes(array);
        return new String(array, Charset.forName("UTF-8"));
    }

    private static String toString(ByteBuf byteBuf)
            throws IOException
    {
        OutputStream outputStream = new ByteArrayOutputStream();
        ByteBuf input = byteBuf.retainedDuplicate();
        try {
            input.readBytes(outputStream, byteBuf.readableBytes());
        }
        finally {
            input.release();
        }
        return outputStream.toString();
    }

    private static ByteBuf encode(ByteBufAllocator allocator, ThriftMessageTransformer transformer, String input)
    {
        ByteBuf inputBuffer = allocator.buffer();
        inputBuffer.writeBytes(input.getBytes());
        return transformer.transform(allocator, inputBuffer);
    }

    private static ByteBuf decode(ByteBufAllocator allocator, ThriftMessageTransformer transformer, ByteBuf input)
    {
        return transformer.untransform(allocator, input);
    }

    private static ByteBuf tryDecode(ByteBufAllocator allocator, ThriftMessageTransformer transformer, ByteBuf input, int sizeLimit)
    {
        return transformer.tryUntransform(allocator, input, sizeLimit);
    }
}
