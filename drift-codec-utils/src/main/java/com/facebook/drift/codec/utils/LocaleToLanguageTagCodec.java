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

import java.util.Locale;

import static java.util.Objects.requireNonNull;

public class LocaleToLanguageTagCodec
        implements ThriftCodec<Locale>
{
    private static final ThriftType THRIFT_TYPE = new ThriftType(ThriftType.STRING, Locale.class);

    @Inject
    public LocaleToLanguageTagCodec(ThriftCatalog thriftCatalog)
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
    public Locale read(TProtocolReader protocol)
            throws Exception
    {
        return stringToLocale(protocol.readString());
    }

    @Override
    public void write(Locale locale, TProtocolWriter protocol)
            throws Exception
    {
        protocol.writeString(localeToString(locale));
    }

    @FromThrift
    public static Locale stringToLocale(String locale)
    {
        requireNonNull(locale, "locale is null");
        return Locale.forLanguageTag(locale);
    }

    @ToThrift
    public static String localeToString(Locale locale)
    {
        requireNonNull(locale, "locale is null");
        return locale.toLanguageTag();
    }
}
