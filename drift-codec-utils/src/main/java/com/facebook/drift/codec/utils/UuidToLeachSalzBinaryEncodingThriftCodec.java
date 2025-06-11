/*
 * Copyright (C) 2020 Facebook, Inc.
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

import javax.inject.Inject;

import java.nio.ByteBuffer;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * This codec will encode UUIDs into the format specified in RFC 4122:
 * <p>
 * https://www.ietf.org/rfc/rfc4122.txt
 * <p>
 * Likewise, it presumes the input is of this format.
 */
public class UuidToLeachSalzBinaryEncodingThriftCodec
        implements ThriftCodec<UUID>
{
    private static final ThriftType THRIFT_TYPE = new ThriftType(ThriftType.BINARY, UUID.class);

    @Inject
    public UuidToLeachSalzBinaryEncodingThriftCodec(ThriftCatalog thriftCatalog)
    {
        thriftCatalog.addDefaultCoercions(getClass());
    }

    @CodecThriftType
    public static ThriftType getThriftType()
    {
        return THRIFT_TYPE;
    }

    @Override
    public ThriftType getType()
    {
        return THRIFT_TYPE;
    }

    @Override
    public UUID read(TProtocolReader protocol)
            throws Exception
    {
        return byteToUuid(protocol.readBinary());
    }

    @Override
    public void write(UUID uuid, TProtocolWriter protocol)
            throws Exception
    {
        protocol.writeBinary(uuidToBytes(uuid));
    }

    @FromThrift
    public static UUID byteToUuid(ByteBuffer bytes)
    {
        requireNonNull(bytes, "bytes is null");
        checkArgument(bytes.limit() == Long.BYTES * 2, "Expected exactly 16 bytes, was: " + bytes.limit());
        return new UUID(bytes.getLong(), bytes.getLong());
    }

    @ToThrift
    public static ByteBuffer uuidToBytes(UUID uuid)
    {
        requireNonNull(uuid, "uuid is null");
        ByteBuffer bytes = ByteBuffer.allocate(Long.BYTES * 2);
        bytes.putLong(uuid.getMostSignificantBits());
        bytes.putLong(uuid.getLeastSignificantBits());
        bytes.position(0);
        return bytes;
    }
}
