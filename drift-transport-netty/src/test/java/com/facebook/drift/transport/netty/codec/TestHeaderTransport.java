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

import com.facebook.drift.TException;
import com.facebook.drift.codec.internal.ProtocolWriter;
import com.facebook.drift.protocol.TMessage;
import com.facebook.drift.protocol.TProtocolWriter;
import com.facebook.drift.transport.netty.buffer.TestingPooledByteBufAllocator;
import com.facebook.drift.transport.netty.ssl.TChannelBufferOutputTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static com.facebook.drift.protocol.TMessageType.CALL;
import static com.facebook.drift.protocol.TMessageType.ONEWAY;
import static com.facebook.drift.transport.netty.codec.HeaderTransport.decodeFrame;
import static com.facebook.drift.transport.netty.codec.HeaderTransport.encodeFrame;
import static com.facebook.drift.transport.netty.codec.HeaderTransport.tryDecodeFrameInfo;
import static com.facebook.drift.transport.netty.codec.Protocol.BINARY;
import static com.facebook.drift.transport.netty.codec.Protocol.FB_COMPACT;
import static com.facebook.drift.transport.netty.codec.ThriftHeaderTransform.ZLIB_TRANSFORM;
import static com.facebook.drift.transport.netty.codec.Transport.HEADER;
import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestHeaderTransport
{
    private static final int FULL_LENGTH = -1;
    private String bigString;

    @BeforeClass
    void setup()
            throws IOException
    {
        bigString = Resources.toString(getResource("TestHeaderTransport.txt"), UTF_8);
    }

    @Test
    public void testNoTransform()
            throws Exception
    {
        assertTryDecodeSequenceId(ImmutableList.of(), 0, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(), 1, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(), 5, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(), 10, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(), 15, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(), 50,
                Optional.of(new FrameInfo("method", CALL, 0xFFAA, HEADER, BINARY, true)));
        assertTryDecodeSequenceId(ImmutableList.of(), 100,
                Optional.of(new FrameInfo("method", CALL, 0xFFAA, HEADER, BINARY, true)));
        assertTryDecodeSequenceId(ImmutableList.of(), FULL_LENGTH,
                Optional.of(new FrameInfo("method", CALL, 0xFFAA, HEADER, BINARY, true)));
        assertRoundTrip(ImmutableList.of());
    }

    @Test
    public void testZlibTransform()
            throws Exception
    {
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 0, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 1, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 5, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 10, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 15, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 50, Optional.empty());
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), 100,
                Optional.of(new FrameInfo("method", CALL, 0xFFAA, HEADER, BINARY, true)));
        assertTryDecodeSequenceId(ImmutableList.of(ZLIB_TRANSFORM), FULL_LENGTH,
                Optional.of(new FrameInfo("method", CALL, 0xFFAA, HEADER, BINARY, true)));
        assertRoundTrip(ImmutableList.of(ZLIB_TRANSFORM));
    }

    private void assertTryDecodeSequenceId(List<ThriftHeaderTransform> transforms, int length, Optional<FrameInfo> expected)
            throws Exception
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf message = createTestFrame(allocator, "method", CALL, 0xFFAA, BINARY, true, transforms);
            try {
                if (length >= 0) {
                    assertDecodeFrameInfo(allocator, message.retainedSlice(0, length), expected);
                }
                else {
                    assertDecodeFrameInfo(allocator, message.retainedDuplicate(), expected);
                }
            }
            finally {
                message.release();
            }
            assertDecodeFrameInfo(allocator,
                    createTestFrame(allocator, "method1", ONEWAY, 123, FB_COMPACT, false, transforms), Optional.of(new FrameInfo("method1", ONEWAY, 123, HEADER, FB_COMPACT, false)));
        }
    }

    private void assertRoundTrip(List<ThriftHeaderTransform> transforms)
            throws Exception
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf expected = createTestMessage(allocator, "method", CALL, 0xFFAA, BINARY);
            ByteBuf message = createTestFrame(allocator, "method", CALL, 0xFFAA, BINARY, true, transforms);
            try {
                assertEquals(message.readerIndex(), 0);
                ThriftFrame result = decodeFrame(allocator, message.retainedDuplicate());
                ByteBuf resultBody = result.getMessage();
                byte[] a = new byte[expected.readableBytes()];
                byte[] b = new byte[resultBody.readableBytes()];
                expected.readBytes(a);
                resultBody.readBytes(b);
                expected.resetReaderIndex();
                resultBody.resetReaderIndex();
                assertTrue(expected.compareTo(resultBody) == 0);
                resultBody.release();
                result.release();
            }
            finally {
                message.release();
                expected.release();
            }
        }
    }

    private static void assertDecodeFrameInfo(ByteBufAllocator bufAllocator, ByteBuf message, Optional<FrameInfo> frameInfo)
    {
        try {
            assertEquals(tryDecodeFrameInfo(bufAllocator, message), frameInfo);
        }
        finally {
            message.release();
        }
    }

    private ByteBuf createTestFrame(
            ByteBufAllocator allocator,
            String methodName,
            byte messageType,
            int sequenceId,
            Protocol protocol,
            boolean supportOutOfOrderResponse,
            List<ThriftHeaderTransform> transforms)
            throws TException
    {
        ThriftFrame frame = new ThriftFrame(
                sequenceId,
                createTestMessage(allocator, methodName, messageType, sequenceId, protocol),
                ImmutableMap.of("header", "value"),
                transforms,
                HEADER,
                protocol,
                supportOutOfOrderResponse);
        return encodeFrame(allocator, frame);
    }

    private ByteBuf createTestMessage(ByteBufAllocator allocator, String methodName, byte messageType, int sequenceId, Protocol protocol)
            throws TException
    {
        TChannelBufferOutputTransport transport = new TChannelBufferOutputTransport(allocator);
        try {
            TProtocolWriter protocolWriter = protocol.createProtocol(transport);
            protocolWriter.writeMessageBegin(new TMessage(methodName, messageType, sequenceId));

            // write the parameters
            ProtocolWriter writer = new ProtocolWriter(protocolWriter);
            writer.writeStructBegin("method_args");
            writer.writeStructEnd();
            writer.writeStructBegin("a_very_long_string");
            writer.writeString(bigString);
            writer.writeStructEnd();

            protocolWriter.writeMessageEnd();
            return transport.getBuffer();
        }
        finally {
            transport.release();
        }
    }
}
