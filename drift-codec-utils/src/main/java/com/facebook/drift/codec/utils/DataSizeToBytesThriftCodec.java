/*
 * Copyright (C) 2012 Facebook, Inc.
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
package com.facebook.drift.codec.utils;

import com.facebook.drift.codec.CodecThriftType;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.internal.coercion.FromThrift;
import com.facebook.drift.codec.internal.coercion.ToThrift;
import com.facebook.drift.codec.metadata.ThriftCatalog;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;
import io.airlift.units.DataSize;

import javax.inject.Inject;

import static io.airlift.units.DataSize.Unit.BYTE;
import static java.util.Objects.requireNonNull;

public class DataSizeToBytesThriftCodec
        implements ThriftCodec<DataSize>
{
    private static final ThriftType THRIFT_TYPE = new ThriftType(ThriftType.DOUBLE, DataSize.class);

    @Inject
    public DataSizeToBytesThriftCodec(ThriftCatalog thriftCatalog)
    {
        thriftCatalog.addDefaultCoercions(getClass());
    }

    @CodecThriftType
    public static ThriftType getThriftType(ThriftCatalog catalog)
    {
        return THRIFT_TYPE;
    }

    @Override
    public ThriftType getType()
    {
        return THRIFT_TYPE;
    }

    @Override
    public DataSize read(TProtocolReader protocol)
            throws Exception
    {
        return bytesToDataSize(protocol.readDouble());
    }

    @Override
    public void write(DataSize dataSize, TProtocolWriter protocol)
            throws Exception
    {
        protocol.writeDouble(dataSizeToBytes(dataSize));
    }

    @FromThrift
    public static DataSize bytesToDataSize(double bytes)
    {
        return new DataSize(bytes, BYTE);
    }

    @ToThrift
    public static double dataSizeToBytes(DataSize dataSize)
    {
        requireNonNull(dataSize, "dataSize is null");
        return dataSize.getValue(BYTE);
    }
}
