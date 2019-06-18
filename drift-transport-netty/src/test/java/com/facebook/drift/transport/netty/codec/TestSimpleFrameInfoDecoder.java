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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import org.testng.annotations.Test;

import static com.facebook.drift.protocol.TMessageType.CALL;
import static com.facebook.drift.transport.netty.codec.Transport.FRAMED;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestSimpleFrameInfoDecoder
{
    private static final String METHOD_NAME = "method";
    private static final int SEQUENCE_ID = 0xAABBCCEE;

    @Test
    public void testDecodeSequenceId()
            throws Exception
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            for (Protocol protocol : Protocol.values()) {
                testDecodeSequenceId(allocator, protocol);
            }
        }
    }

    private static void testDecodeSequenceId(ByteBufAllocator allocator, Protocol protocol)
            throws TException
    {
        FrameInfoDecoder decoder = new SimpleFrameInfoDecoder(FRAMED, protocol, true);
        ByteBuf message = createTestMessage(allocator, protocol);
        try {
            assertFalse(decoder.tryDecodeFrameInfo(allocator, message.slice(0, 0)).isPresent());
            assertFalse(decoder.tryDecodeFrameInfo(allocator, message.slice(0, 1)).isPresent());
            assertFalse(decoder.tryDecodeFrameInfo(allocator, message.slice(0, 2)).isPresent());
            assertFalse(decoder.tryDecodeFrameInfo(allocator, message.slice(0, 5)).isPresent());
            assertTrue(decoder.tryDecodeFrameInfo(allocator, message.slice(0, message.readableBytes())).isPresent());
            assertTrue(decoder.tryDecodeFrameInfo(allocator, message).isPresent());
            assertEquals(decoder.tryDecodeFrameInfo(allocator, message).get(), new FrameInfo(METHOD_NAME, CALL, SEQUENCE_ID, FRAMED, protocol, true));
        }
        finally {
            message.release();
        }
    }

    private static ByteBuf createTestMessage(ByteBufAllocator allocator, Protocol protocol)
            throws TException
    {
        TChannelBufferOutputTransport transport = new TChannelBufferOutputTransport(allocator);
        try {
            TProtocolWriter protocolWriter = protocol.createProtocol(transport);
            protocolWriter.writeMessageBegin(new TMessage("method", CALL, SEQUENCE_ID));

            // write the parameters
            ProtocolWriter writer = new ProtocolWriter(protocolWriter);
            writer.writeStructBegin("method_args");
            writer.writeStructEnd();

            protocolWriter.writeMessageEnd();
            return transport.getBuffer();
        }
        finally {
            transport.release();
        }
    }
}
