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
package com.facebook.drift.codec.guice;

import com.facebook.drift.codec.BonkConstructor;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.customizations.ValueClass;
import com.facebook.drift.codec.customizations.ValueClassCodec;
import com.facebook.drift.protocol.TCompactProtocol;
import com.facebook.drift.protocol.TMemoryBuffer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Stage;
import com.google.inject.TypeLiteral;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestThriftCodecModule
{
    @Test
    public void testThriftClientAndServerModules()
            throws Exception
    {
        Injector injector = Guice.createInjector(
                Stage.PRODUCTION,
                new ThriftCodecModule(),
                binder -> {
                    ThriftCodecBinder.thriftCodecBinder(binder).bindThriftCodec(BonkConstructor.class);
                    ThriftCodecBinder.thriftCodecBinder(binder).bindListThriftCodec(BonkConstructor.class);
                    ThriftCodecBinder.thriftCodecBinder(binder).bindMapThriftCodec(String.class, BonkConstructor.class);

                    ThriftCodecBinder.thriftCodecBinder(binder).bindThriftCodec(new TypeLiteral<Map<Integer, List<String>>>() {});

                    ThriftCodecBinder.thriftCodecBinder(binder).bindCustomThriftCodec(new ValueClassCodec());
                });

        testRoundTripSerialize(
                injector.getInstance(Key.get(new TypeLiteral<ThriftCodec<BonkConstructor>>() {})),
                new BonkConstructor("message", 42));

        testRoundTripSerialize(
                injector.getInstance(Key.get(new TypeLiteral<ThriftCodec<List<BonkConstructor>>>() {})),
                ImmutableList.of(new BonkConstructor("one", 1), new BonkConstructor("two", 2)));

        testRoundTripSerialize(
                injector.getInstance(Key.get(new TypeLiteral<ThriftCodec<Map<String, BonkConstructor>>>() {})),
                ImmutableMap.of("uno", new BonkConstructor("one", 1), "dos", new BonkConstructor("two", 2)));

        testRoundTripSerialize(
                injector.getInstance(Key.get(new TypeLiteral<ThriftCodec<Map<Integer, List<String>>>>() {})),
                ImmutableMap.of(1, ImmutableList.of("one", "uno"), 2, ImmutableList.of("two", "dos")));

        testRoundTripSerialize(
                injector.getInstance(Key.get(new TypeLiteral<ThriftCodec<ValueClass>>() {})),
                new ValueClass("my value"));
    }

    public static <T> void testRoundTripSerialize(ThriftCodec<T> codec, T value)
            throws Exception
    {
        // write value
        TMemoryBuffer transport = new TMemoryBuffer(10 * 1024);
        TCompactProtocol protocol = new TCompactProtocol(transport);
        codec.write(value, protocol);

        // read value back
        T copy = codec.read(protocol);
        assertNotNull(copy);

        // verify they are the same
        assertEquals(copy, value);
    }
}
