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

import com.facebook.drift.codec.internal.ProtocolWriter;
import com.facebook.drift.protocol.TMessage;
import com.facebook.drift.protocol.TProtocolWriter;
import com.facebook.drift.transport.netty.buffer.TestingPooledByteBufAllocator;
import com.facebook.drift.transport.netty.ssl.TChannelBufferOutputTransport;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static com.facebook.drift.protocol.TMessageType.CALL;
import static com.facebook.drift.protocol.TMessageType.ONEWAY;
import static com.facebook.drift.transport.netty.codec.Protocol.BINARY;
import static com.facebook.drift.transport.netty.codec.Transport.FRAMED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

public class TestThriftFramedDecoder
{
    @Test
    public void testBelowLimit()
    {
        byte[] first = new byte[] {1, 2, 3, 4, 5};
        byte[] second = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] third = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};

        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf buffer = allocator.buffer(1024);

            writeLengthPrefixedFrame(buffer, first);
            writeLengthPrefixedFrame(buffer, second);
            writeLengthPrefixedFrame(buffer, third);

            ThriftFramedDecoder decoder = createDecoder(third.length);

            ByteBuf decodedFirst = decode(allocator, decoder, buffer);
            assertNotNull(decodedFirst);
            assertContentEquals(decodedFirst, first);
            decodedFirst.release();

            ByteBuf decodedSecond = decode(allocator, decoder, buffer);
            assertNotNull(decodedSecond);
            assertContentEquals(decodedSecond, second);
            decodedSecond.release();

            ByteBuf decodedThird = decode(allocator, decoder, buffer);
            assertNotNull(decodedThird);
            assertContentEquals(decodedThird, third);
            decodedThird.release();

            buffer.release();
        }
    }

    @Test
    public void testChunked()
    {
        byte[] first = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] second = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        byte[] third = new byte[] {5, 4, 3};

        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            ByteBuf buffer = allocator.buffer(1024);

            ThriftFramedDecoder decoder = createDecoder(second.length);
            ByteBuf decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);

            // write a partial frame length
            buffer.writeByte(0xAB);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            assertEquals(buffer.readerIndex(), 0);
            assertEquals(buffer.writerIndex(), 1);

            // write only a frame length
            buffer.writerIndex(0);
            buffer.writeInt(first.length);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            assertEquals(buffer.readerIndex(), 0);
            assertEquals(buffer.writerIndex(), Integer.BYTES);

            // start writing a frame
            buffer.writeBytes(first, 0, 5);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            assertEquals(buffer.readerIndex(), 0);
            assertEquals(buffer.writerIndex(), Integer.BYTES + 5);

            // finish writing a frame
            buffer.writeBytes(first, 5, first.length - 5);
            decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, first);
            decoded.release();

            // write the first frame
            writeLengthPrefixedFrame(buffer, second);
            // start writing the second frame
            buffer.writeInt(third.length);
            buffer.writeBytes(third, 0, 1);
            // decode the first frame
            decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, second);
            decoded.release();

            // try decode the second frame
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);

            // finish writing the second frame
            buffer.writeBytes(third, 1, third.length - 1);
            decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, third);
            decoded.release();

            assertEquals(buffer.readerIndex(), buffer.writerIndex());

            buffer.release();
        }
    }

    @Test
    public void testBeyondLimit()
            throws Exception
    {
        try (TestingPooledByteBufAllocator allocator = new TestingPooledByteBufAllocator()) {
            byte[] small = new byte[] {5, 4, 3};
            byte[] firstLargeFrame = createTestFrame(allocator, "first_method", 1, CALL);
            byte[] secondLargeFrame = createTestFrame(allocator, "second_method", 2, ONEWAY);
            byte[] invalidLargeFrame = createInvalidFrame();
            FrameInfo firstFrameInfo = new FrameInfo("first_method", CALL, 1, FRAMED, BINARY, true);
            FrameInfo secondFrameInfo = new FrameInfo("second_method", ONEWAY, 2, FRAMED, BINARY, true);

            ByteBuf buffer = allocator.buffer(1024);

            ThriftFramedDecoder decoder = createDecoder(firstLargeFrame.length - 5);

            // write a small frame
            writeLengthPrefixedFrame(buffer, small);
            ByteBuf decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, small);
            decoded.release();

            // write a large frame in a single chunk
            writeLengthPrefixedFrame(buffer, firstLargeFrame);
            writeLengthPrefixedFrame(buffer, small);
            try {
                decode(allocator, decoder, buffer);
                fail("failure expected");
            }
            catch (RuntimeException e) {
                assertThat(e).isInstanceOf(FrameTooLargeException.class)
                        .hasFieldOrPropertyWithValue("frameInfo", Optional.of(firstFrameInfo));
            }
            assertEquals(buffer.readableBytes(), Integer.BYTES + small.length);
            decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, small);
            decoded.release();

            // write the first large frame in multiple chunks
            buffer.writeInt(secondLargeFrame.length);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            buffer.writeBytes(secondLargeFrame, 0, 1);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            buffer.writeBytes(secondLargeFrame, 1, 2);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);
            // write the second large frame in multiple chunks
            buffer.writeBytes(secondLargeFrame, 3, secondLargeFrame.length - 3);
            buffer.writeInt(firstLargeFrame.length);
            buffer.writeBytes(firstLargeFrame, 0, 5);

            // decode the first large frame
            try {
                decode(allocator, decoder, buffer);
                fail("failure expected");
            }
            catch (RuntimeException e) {
                assertThat(e).isInstanceOf(FrameTooLargeException.class)
                        .hasFieldOrPropertyWithValue("frameInfo", Optional.of(secondFrameInfo));
            }
            assertEquals(buffer.readableBytes(), Integer.BYTES + 5);

            // try decode the second large frame
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);

            // finish the second large frame
            buffer.writeBytes(firstLargeFrame, 5, firstLargeFrame.length - 5);

            // decode the second large frame
            try {
                decode(allocator, decoder, buffer);
                fail("failure expected");
            }
            catch (RuntimeException e) {
                assertThat(e).isInstanceOf(FrameTooLargeException.class)
                        .hasFieldOrPropertyWithValue("frameInfo", Optional.of(firstFrameInfo));
            }
            assertEquals(buffer.readableBytes(), 0);

            // write an invalid large frame in a single chunk
            writeLengthPrefixedFrame(buffer, invalidLargeFrame);
            writeLengthPrefixedFrame(buffer, small);
            try {
                decode(allocator, decoder, buffer);
                fail("failure expected");
            }
            catch (RuntimeException e) {
                assertThat(e).isInstanceOf(FrameTooLargeException.class)
                        // frameInfo cannot be decoded for an invalid frame
                        .hasFieldOrPropertyWithValue("frameInfo", Optional.empty());
            }
            assertEquals(buffer.readableBytes(), Integer.BYTES + small.length);
            decoded = decode(allocator, decoder, buffer);
            assertNotNull(decoded);
            assertContentEquals(decoded, small);
            decoded.release();

            // write an invalid large frame in multiple chunks
            buffer.writeInt(invalidLargeFrame.length);
            buffer.writeBytes(invalidLargeFrame, 0, invalidLargeFrame.length / 2);
            decoded = decode(allocator, decoder, buffer);
            assertNull(decoded);

            buffer.writeBytes(invalidLargeFrame, invalidLargeFrame.length / 2, invalidLargeFrame.length - invalidLargeFrame.length / 2);
            try {
                decode(allocator, decoder, buffer);
                fail("failure expected");
            }
            catch (RuntimeException e) {
                assertThat(e).isInstanceOf(FrameTooLargeException.class)
                        // frame info cannot be decoded for an invalid frame
                        .hasFieldOrPropertyWithValue("frameInfo", Optional.empty());
            }
            assertEquals(buffer.readableBytes(), 0);

            buffer.release();
        }
    }

    private static ThriftFramedDecoder createDecoder(int maxFrameSizeInBytes)
    {
        return new ThriftFramedDecoder(new SimpleFrameInfoDecoder(FRAMED, BINARY, true), maxFrameSizeInBytes);
    }

    private static ByteBuf decode(ByteBufAllocator bufAllocator, ThriftFramedDecoder decoder, ByteBuf input)
    {
        List<Object> output = new ArrayList<>(1);
        decoder.decode(createTestingChannelHandlerContext(bufAllocator), input, output);
        if (output.isEmpty()) {
            return null;
        }
        assertEquals(output.size(), 1);
        return (ByteBuf) output.get(0);
    }

    private static byte[] createTestFrame(ByteBufAllocator allocator, String methodName, int sequenceId, byte messageType)
            throws Exception
    {
        TChannelBufferOutputTransport transport = new TChannelBufferOutputTransport(allocator);
        try {
            TProtocolWriter protocolWriter = BINARY.createProtocol(transport);
            protocolWriter.writeMessageBegin(new TMessage(methodName, messageType, sequenceId));

            // write the parameters
            ProtocolWriter writer = new ProtocolWriter(protocolWriter);
            writer.writeStructBegin(methodName + "_args");
            writer.writeStructEnd();

            protocolWriter.writeMessageEnd();
            ByteBuf buffer = transport.getBuffer();
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            buffer.release();
            return result;
        }
        finally {
            transport.release();
        }
    }

    private static byte[] createInvalidFrame()
            throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(output);
        // write fake version
        dataOutput.writeInt(0xAABBCCEE);
        // write invalid string size
        dataOutput.writeInt(Integer.MAX_VALUE);
        // write some random data
        byte[] data = new byte[1024];
        ThreadLocalRandom.current().nextBytes(data);
        dataOutput.write(data);
        dataOutput.close();
        output.close();
        return output.toByteArray();
    }

    private static void writeLengthPrefixedFrame(ByteBuf buffer, byte[] frame)
    {
        buffer.writeInt(frame.length);
        buffer.writeBytes(frame);
    }

    private static void assertContentEquals(ByteBuf buffer, byte[] expectedContent)
    {
        assertEquals(buffer.readableBytes(), expectedContent.length);
        byte[] actual = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), actual);
        assertEquals(actual, expectedContent);
    }

    private static ChannelHandlerContext createTestingChannelHandlerContext(ByteBufAllocator bufAllocator)
    {
        return new ChannelHandlerContext()
        {
            @Override
            public Channel channel()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public EventExecutor executor()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public String name()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandler handler()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRemoved()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelRegistered()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelUnregistered()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelActive()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelInactive()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireExceptionCaught(Throwable cause)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireUserEventTriggered(Object evt)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelRead(Object msg)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelReadComplete()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext fireChannelWritabilityChanged()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext read()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelHandlerContext flush()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPipeline pipeline()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBufAllocator alloc()
            {
                return bufAllocator;
            }

            @Override
            public <T> Attribute<T> attr(AttributeKey<T> key)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> boolean hasAttr(AttributeKey<T> key)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture disconnect()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture close()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture deregister()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture disconnect(ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture close(ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture deregister(ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture write(Object msg)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture write(Object msg, ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPromise newPromise()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelProgressivePromise newProgressivePromise()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture newSucceededFuture()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelFuture newFailedFuture(Throwable cause)
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public ChannelPromise voidPromise()
            {
                throw new UnsupportedOperationException();
            }
        };
    }
}
