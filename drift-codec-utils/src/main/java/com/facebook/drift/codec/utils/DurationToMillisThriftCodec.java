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

import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.internal.coercion.FromThrift;
import com.facebook.drift.codec.internal.coercion.ToThrift;
import com.facebook.drift.codec.metadata.ThriftCatalog;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;
import io.airlift.units.Duration;

import javax.inject.Inject;

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DurationToMillisThriftCodec
        implements ThriftCodec<Duration>
{
    @Inject
    public DurationToMillisThriftCodec(ThriftCatalog thriftCatalog)
    {
        thriftCatalog.addDefaultCoercions(getClass());
    }

    @Override
    public ThriftType getType()
    {
        return new ThriftType(ThriftType.DOUBLE, Duration.class);
    }

    @Override
    public Duration read(TProtocolReader protocol)
            throws Exception
    {
        return millisToDuration(protocol.readDouble());
    }

    @Override
    public void write(Duration duration, TProtocolWriter protocol)
            throws Exception
    {
        protocol.writeDouble(durationToMillis(duration));
    }

    @FromThrift
    public static Duration millisToDuration(double millis)
    {
        return new Duration(millis, MILLISECONDS);
    }

    @ToThrift
    public static double durationToMillis(Duration duration)
    {
        requireNonNull(duration, "duration is null");
        return duration.getValue(MILLISECONDS);
    }
}
