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
package com.facebook.drift.codec.internal.reflection;

import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.codec.metadata.FieldKind;
import com.facebook.drift.codec.metadata.ThriftExtraction;
import com.facebook.drift.codec.metadata.ThriftFieldExtractor;
import com.facebook.drift.codec.metadata.ThriftFieldMetadata;
import com.facebook.drift.codec.metadata.ThriftMethodExtractor;
import com.facebook.drift.codec.metadata.ThriftStructMetadata;
import com.facebook.drift.codec.metadata.ThriftType;
import com.google.common.collect.ImmutableSortedMap;

import java.lang.reflect.InvocationTargetException;
import java.util.SortedMap;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;

public abstract class AbstractReflectionThriftCodec<T>
        implements ThriftCodec<T>
{
    protected final ThriftStructMetadata metadata;
    protected final SortedMap<Short, ThriftCodec<?>> fields;

    protected AbstractReflectionThriftCodec(ThriftCodecManager manager, ThriftStructMetadata metadata)
    {
        this.metadata = metadata;

        ImmutableSortedMap.Builder<Short, ThriftCodec<?>> fields = ImmutableSortedMap.naturalOrder();
        for (ThriftFieldMetadata fieldMetadata : metadata.getFields(FieldKind.THRIFT_FIELD)) {
            fields.put(fieldMetadata.getId(), manager.getCodec(fieldMetadata.getThriftType()));
        }
        this.fields = fields.build();
    }

    @Override
    public ThriftType getType()
    {
        return ThriftType.struct(metadata);
    }

    protected Object getFieldValue(Object instance, ThriftFieldMetadata field)
            throws Exception
    {
        try {
            ThriftExtraction extraction = field.getExtraction()
                    .orElseThrow(() -> new IllegalAccessException("No extraction present for " + field));
            if (extraction instanceof ThriftFieldExtractor) {
                ThriftFieldExtractor thriftFieldExtractor = (ThriftFieldExtractor) extraction;
                return thriftFieldExtractor.getField().get(instance);
            }
            else if (extraction instanceof ThriftMethodExtractor) {
                ThriftMethodExtractor thriftMethodExtractor = (ThriftMethodExtractor) extraction;
                return thriftMethodExtractor.getMethod().invoke(instance);
            }
            throw new IllegalAccessException("Unsupported field extractor type " + extraction.getClass().getName());
        }
        catch (InvocationTargetException e) {
            throwIfUnchecked(e.getCause());
            throwIfInstanceOf(e.getCause(), Exception.class);
            throw e;
        }
    }
}
