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

import io.netty.buffer.ByteBuf;

public interface TTransport
{
    void read(byte[] buf, int off, int len)
            throws TTransportException;

    void write(byte[] buf, int off, int len)
            throws TTransportException;

    default void write(byte[] buf)
            throws TTransportException
    {
        write(buf, 0, buf.length);
    }

    default void write(ByteBuf buffer)
            throws TTransportException
    {
        if (buffer.hasArray()) {
            write(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(), buffer.readableBytes());
        }
        else {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes);
            write(bytes, 0, bytes.length);
        }
    }
}
