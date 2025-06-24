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

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class TMemoryBufferWriteOnly
        implements TTransport
{
    private ByteArrayDataOutput data;

    public TMemoryBufferWriteOnly(int size)
    {
        data = ByteStreams.newDataOutput(size);
    }

    @Override
    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        throw new UnsupportedOperationException("Read operation is not supported");
    }

    @Override
    public void write(byte[] buf, int off, int len)
            throws TTransportException
    {
        data.write(buf, off, len);
    }

    public byte[] getBytes()
    {
        return data.toByteArray();
    }
}
