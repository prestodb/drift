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
package com.facebook.drift.codec.internal.builtin;

import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.internal.coercion.FromThrift;
import com.facebook.drift.codec.internal.coercion.ToThrift;
import com.facebook.drift.codec.metadata.ThriftCatalog;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;

import java.net.URI;

import static java.util.Objects.requireNonNull;

public class UriThriftCodec
        implements ThriftCodec<URI>
{
    public UriThriftCodec(ThriftCatalog thriftCatalog)
    {
        requireNonNull(thriftCatalog, "thriftCatalog is null");
        thriftCatalog.addDefaultCoercions(getClass());
    }

    @Override
    public ThriftType getType()
    {
        return new ThriftType(ThriftType.STRING, URI.class);
    }

    @Override
    public URI read(TProtocolReader protocol)
            throws Exception
    {
        requireNonNull(protocol, "protocol is null");
        return URI.create(protocol.readString());
    }

    @Override
    public void write(URI value, TProtocolWriter protocol)
            throws Exception
    {
        requireNonNull(value, "value is null");
        requireNonNull(protocol, "protocol is null");
        protocol.writeString(value.toString());
    }

    @FromThrift
    public static URI stringToUri(String uri)
    {
        return URI.create(uri);
    }

    @ToThrift
    public static String uriToString(URI uri)
    {
        return uri.toString();
    }
}
