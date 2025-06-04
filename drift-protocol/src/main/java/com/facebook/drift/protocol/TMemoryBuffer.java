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

import java.io.UnsupportedEncodingException;

public class TMemoryBuffer
        implements TTransport
{
    // The contents of the buffer
    private TByteArrayOutputStream data;

    // Position to read next byte from
    private int position;

    public TMemoryBuffer(int size)
    {
        reset(size);
    }

    @Override
    public void read(byte[] buf, int off, int len)
            throws TTransportException
    {
        byte[] src = data.get();
        int amtToRead = (len > data.len() - position ? data.len() - position : len);
        if (amtToRead > 0) {
            System.arraycopy(src, position, buf, off, amtToRead);
            position += amtToRead;
        }
    }

    @Override
    public void write(byte[] buf, int off, int len)
    {
        data.write(buf, off, len);
    }

    /**
     * Output the contents of the memory buffer as a String, using the supplied encoding
     *
     * @param enc the encoding to use
     * @return the contents of the memory buffer as a String
     */
    public String toString(String enc)
            throws UnsupportedEncodingException
    {
        return data.toString(enc);
    }

    public int length()
    {
        return data.len();
    }

    public void reset(int size)
    {
        data = new TByteArrayOutputStream(size);
        position = 0;
    }

    public byte[] getBytes()
    {
        return data.get();
    }

    public int getArrayPos()
    {
        return position;
    }
}
