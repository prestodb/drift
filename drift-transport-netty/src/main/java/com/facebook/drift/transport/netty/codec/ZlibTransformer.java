/*
 * Copyright (C) 2013 Facebook, Inc.
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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class ZlibTransformer
        implements ThriftMessageTransformer
{
    public static final ZlibTransformer ZLIB_TRANSFORMER = new ZlibTransformer(512);
    private final int zipBlockSize;

    public ZlibTransformer(int zipBlockSize)
    {
        this.zipBlockSize = zipBlockSize;
    }

    @Override
    public ByteBuf transform(ByteBufAllocator bufAllocator, ByteBuf input)
    {
        ByteBuf output = bufAllocator.buffer(zipBlockSize);
        try (DeflaterOutputStream outputStream = new DeflaterOutputStream(new ByteBufOutputStream(output))) {
            input.readBytes(outputStream, input.readableBytes());
        }
        catch (IOException e) {
            output.release();
            throw new UncheckedIOException(e);
        }
        finally {
            input.release();
        }
        return output;
    }

    @Override
    public ByteBuf untransform(ByteBufAllocator bufAllocator, ByteBuf input)
    {
        ByteBuf output = bufAllocator.buffer(zipBlockSize);
        try (InflaterInputStream inputStream = new InflaterInputStream(new ByteBufInputStream(input))) {
            while (inputStream.available() > 0) {
                output.writeBytes(inputStream, zipBlockSize);
            }
            return output;
        }
        catch (IOException e) {
            output.release();
            throw new UncheckedIOException(e);
        }
        finally {
            input.release();
        }
    }

    @Override
    public ByteBuf tryUntransform(ByteBufAllocator bufAllocator, ByteBuf input, int byteLimit)
    {
        ByteBuf output = bufAllocator.buffer(zipBlockSize);
        try (InflaterInputStream inputStream = new InflaterInputStream(new ByteBufInputStream(input))) {
            while (inputStream.available() > 0) {
                output.writeBytes(inputStream, zipBlockSize);
                if (byteLimit > 0 && output.readableBytes() >= byteLimit) {
                    break;
                }
            }
            return output;
        }
        catch (IOException e) {
            return output;
        }
        finally {
            input.release();
        }
    }
}
