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
package com.facebook.drift.codec;

import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;
import com.google.common.reflect.TypeToken;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.lang.reflect.Type;

/**
 * A placeholder for a{@link ThriftCodec} that defers computation of the real codec
 * until it is actually used, and then just delegates to that codec.
 * <p>
 * This is used to break the cycle when computing the codec for a recursive type
 * tries to compute codecs for all of its fields.
 */
public class DelegateCodec<T>
        implements ThriftCodec<T>
{
    private final ThriftCodecManager codecManager;
    private final TypeToken<T> typeToken;

    @SuppressFBWarnings("EI_EXPOSE_REP2")
    public DelegateCodec(ThriftCodecManager codecManager, Type javaType)
    {
        this.codecManager = codecManager;
        this.typeToken = (TypeToken<T>) TypeToken.of(javaType);
    }

    @Override
    public ThriftType getType()
    {
        return getCodec().getType();
    }

    @Override
    public T read(TProtocolReader protocol)
            throws Exception
    {
        return getCodec().read(protocol);
    }

    @Override
    public void write(T value, TProtocolWriter protocol)
            throws Exception
    {
        getCodec().write(value, protocol);
    }

    @Override
    public boolean isNull(T value)
    {
        return getCodec().isNull(value);
    }

    private ThriftCodec<T> getCodec()
    {
        ThriftCodec<T> codec = codecManager.getCachedCodecIfPresent(typeToken);
        if (codec == null) {
            throw new IllegalStateException("Tried to encode/decode using a DelegateCodec before the target codec was built (likely a bug in recursive type support)");
        }
        return codec;
    }
}
