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
package com.facebook.drift.codec.internal;

import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.metadata.ThriftEnumMetadata;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;

import javax.annotation.concurrent.Immutable;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * EnumThriftCodec is a codec for Java enum types.  An enum is encoded as an I32 in Thrift, and this
 * class handles converting this vale to a Java enum constant.
 */
@Immutable
public class EnumThriftCodec<T extends Enum<T>>
        implements ThriftCodec<T>
{
    private final ThriftType type;
    private final ThriftEnumMetadata<T> enumMetadata;

    public EnumThriftCodec(ThriftType type)
    {
        this.type = type;
        enumMetadata = (ThriftEnumMetadata<T>) type.getEnumMetadata();
    }

    @Override
    public ThriftType getType()
    {
        return type;
    }

    @Override
    public T read(TProtocolReader protocol)
            throws Exception
    {
        int enumValue = protocol.readI32();
        T enumConstant = enumMetadata.getByEnumValue().get(enumValue);
        if (enumConstant != null) {
            return enumConstant;
        }
        return enumMetadata.getUnknownEnumConstant()
                .orElseThrow(() -> new UnknownEnumValueException(format("Enum %s does not have a constant for value: %s", enumMetadata.getEnumClass().getName(), enumValue)));
    }

    @Override
    public void write(T enumConstant, TProtocolWriter protocol)
            throws Exception
    {
        requireNonNull(enumConstant, "enumConstant is null");
        protocol.writeI32(enumMetadata.getByEnumConstant().get(enumConstant));
    }
}
