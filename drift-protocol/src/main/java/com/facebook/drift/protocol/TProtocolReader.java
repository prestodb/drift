/*
 * Copyright (C) 2017 Facebook, Inc.
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
import io.netty.buffer.Unpooled;

import java.nio.ByteBuffer;
import java.util.List;

public interface TProtocolReader
{
    TMessage readMessageBegin()
            throws TException;

    void readMessageEnd()
            throws TException;

    TStruct readStructBegin()
            throws TException;

    void readStructEnd()
            throws TException;

    TField readFieldBegin()
            throws TException;

    void readFieldEnd()
            throws TException;

    TMap readMapBegin()
            throws TException;

    void readMapEnd()
            throws TException;

    TList readListBegin()
            throws TException;

    void readListEnd()
            throws TException;

    TSet readSetBegin()
            throws TException;

    void readSetEnd()
            throws TException;

    boolean readBool()
            throws TException;

    byte readByte()
            throws TException;

    short readI16()
            throws TException;

    int readI32()
            throws TException;

    long readI64()
            throws TException;

    float readFloat()
            throws TException;

    double readDouble()
            throws TException;

    String readString()
            throws TException;

    ByteBuffer readBinary()
            throws TException;

    default List<ByteBuf> readBinaryAsByteBufList()
            throws TException
    {
        ByteBuf buf = readBinaryAsByteBuf();
        return ImmutableList.of(buf);
    }

    default ByteBuf readBinaryAsByteBuf()
            throws TException

    {
        ByteBuffer buffer = readBinary();
        if (buffer.hasArray()) {
            return Unpooled.wrappedBuffer(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
        }
        else {
            ByteBuf byteBuf = Unpooled.buffer(buffer.remaining());
            byteBuf.writeBytes(buffer);
            return byteBuf;
        }
    }

    int readBinary(byte[] buf, int offset)
            throws TException;
}
