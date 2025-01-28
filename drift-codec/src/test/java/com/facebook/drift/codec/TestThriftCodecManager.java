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

import com.facebook.drift.codec.internal.EnumThriftCodec;
import com.facebook.drift.codec.internal.coercion.DefaultJavaCoercions;
import com.facebook.drift.codec.metadata.ThriftCatalog;
import com.facebook.drift.codec.metadata.ThriftEnumMetadata;
import com.facebook.drift.codec.metadata.ThriftEnumMetadataBuilder;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TBinaryProtocol;
import com.facebook.drift.protocol.TCompactProtocol;
import com.facebook.drift.protocol.TFacebookCompactProtocol;
import com.facebook.drift.protocol.TMemoryBuffer;
import com.facebook.drift.protocol.TProtocol;
import com.facebook.drift.protocol.TTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestThriftCodecManager
{
    public static final String UTF8_TEST_STRING = "A" + "\u00ea" + "\u00f1" + "\u00fc" + "C";
    private ThriftCodecManager codecManager;

    @BeforeMethod
    protected void setUp()
    {
        codecManager = new ThriftCodecManager((codecManager, metadata) -> {
            throw new UnsupportedOperationException();
        });
        ThriftCatalog catalog = codecManager.getCatalog();
        catalog.addDefaultCoercions(DefaultJavaCoercions.class);
        ThriftType fruitType = catalog.getThriftType(Fruit.class);
        codecManager.addCodec(new EnumThriftCodec<Fruit>(fruitType));
    }

    @Test
    public void testBasicTypes()
            throws Exception
    {
        testRoundTripSerialize(true);
        testRoundTripSerialize(false);
        testRoundTripSerialize((byte) 100);
        testRoundTripSerialize((short) 1000);
        testRoundTripSerialize(10000);
        testRoundTripSerialize((long) 10000000);
        testRoundTripSerialize(42.42d);
        testRoundTripSerialize(58.7f);
        testRoundTripSerialize("some string");
        testRoundTripSerialize(UTF8_TEST_STRING);
    }

    @Test
    public void testBasicThriftTypes()
            throws Exception
    {
        testRoundTripSerialize(ThriftType.BOOL, true);
        testRoundTripSerialize(ThriftType.BOOL, false);
        testRoundTripSerialize(ThriftType.BYTE, (byte) 100);
        testRoundTripSerialize(ThriftType.I16, (short) 1000);
        testRoundTripSerialize(ThriftType.I32, 10000);
        testRoundTripSerialize(ThriftType.I64, (long) 10000000);
        testRoundTripSerialize(ThriftType.DOUBLE, 42.42d);
        testRoundTripSerialize(ThriftType.FLOAT, 58.7f);
        testRoundTripSerialize(ThriftType.STRING, "some string");
        testRoundTripSerialize(ThriftType.STRING, UTF8_TEST_STRING);
    }

    @Test
    public void testEnum()
            throws Exception
    {
        ThriftEnumMetadata<Fruit> fruitEnumMetadata = ThriftEnumMetadataBuilder.thriftEnumMetadata(Fruit.class);
        ThriftEnumMetadata<Letter> letterEnumMetadata = ThriftEnumMetadataBuilder.thriftEnumMetadata(Letter.class);
        testRoundTripSerialize(Fruit.CHERRY);
        testRoundTripSerialize(Letter.C);
        testRoundTripSerialize(ThriftType.enumType(fruitEnumMetadata), Fruit.CHERRY);
        testRoundTripSerialize(ThriftType.enumType(letterEnumMetadata), Letter.C);
        testRoundTripSerialize(ThriftType.list(ThriftType.enumType(fruitEnumMetadata)), ImmutableList.copyOf(Fruit.values()));
        testRoundTripSerialize(ThriftType.list(ThriftType.enumType(letterEnumMetadata)), ImmutableList.copyOf(Letter.values()));

        testRoundTripSerialize(ThriftType.I32, 500, ThriftType.enumType(letterEnumMetadata), Letter.UNKNOWN);
        testRoundTripSerialize(
                ThriftType.list(ThriftType.I32.coerceTo(Integer.class)),
                ImmutableList.of(-100, -1, 0, 1, 67, 65, 66, 1000),
                ThriftType.list(ThriftType.enumType(letterEnumMetadata)),
                ImmutableList.of(Letter.UNKNOWN, Letter.UNKNOWN, Letter.UNKNOWN, Letter.UNKNOWN, Letter.C, Letter.A, Letter.B, Letter.UNKNOWN));
    }

    @Test
    public void testCollectionThriftTypes()
            throws Exception
    {
        testRoundTripSerialize(ThriftType.set(ThriftType.STRING), ImmutableSet.of("some string", "another string"));
        testRoundTripSerialize(ThriftType.list(ThriftType.STRING), ImmutableList.of("some string", "another string"));
        testRoundTripSerialize(ThriftType.map(ThriftType.STRING, ThriftType.STRING), ImmutableMap.of("1", "one", "2", "two"));
    }

    @Test
    public void testCoercedCollection()
            throws Exception
    {
        testRoundTripSerialize(ThriftType.set(ThriftType.I32.coerceTo(Integer.class)), ImmutableSet.of(1, 2, 3));
        testRoundTripSerialize(ThriftType.list(ThriftType.I32.coerceTo(Integer.class)), ImmutableList.of(4, 5, 6));
        testRoundTripSerialize(ThriftType.map(ThriftType.I32.coerceTo(Integer.class), ThriftType.I32.coerceTo(Integer.class)), ImmutableMap.of(1, 2, 2, 4, 3, 9));
    }

    @Test
    public void testAddStructCodec()
            throws Exception
    {
        BonkField bonk = new BonkField("message", 42);

        // no codec for BonkField so this will fail
        try {
            testRoundTripSerialize(bonk);
            fail("Expected exception");
        }
        catch (Exception ignored) {
        }

        // add the codec
        ThriftType type = codecManager.getCatalog().getThriftType(BonkField.class);
        codecManager.addCodec(new BonkFieldThriftCodec(type));

        // try again
        testRoundTripSerialize(bonk);
    }

    @Test
    public void testAddUnionCodec()
            throws Exception
    {
        UnionField union = new UnionField();
        union.id = 1;
        union.stringValue = "Hello, World";

        // no codec for UnionField so this will fail
        try {
            testRoundTripSerialize(union);
            fail("Expected exception");
        }
        catch (Exception ignored) {
        }

        // add the codec
        ThriftType type = codecManager.getCatalog().getThriftType(UnionField.class);
        codecManager.addCodec(new UnionFieldThriftCodec(type, codecManager.getCodec(Fruit.class)));

        // try again
        testRoundTripSerialize(union);

        union = new UnionField();
        union.id = 2;
        union.longValue = 4815162342L;

        testRoundTripSerialize(union);

        union = new UnionField();
        union.id = 3;
        union.fruitValue = Fruit.BANANA;

        testRoundTripSerialize(union);
    }

    private <T> void testRoundTripSerialize(T value)
            throws Exception
    {
        ThriftType type = codecManager.getCatalog().getThriftType(value.getClass());
        testRoundTripSerialize(type, value);
    }

    private <T> void testRoundTripSerialize(ThriftType type, T value)
            throws Exception
    {
        testRoundTripSerialize(type, value, type, value);
    }

    private <T> void testRoundTripSerialize(ThriftType actualType, T actualValue, ThriftType expectedType, T expectedValue)
            throws Exception
    {
        testRoundTripSerialize(actualType, actualValue, expectedType, expectedValue, TBinaryProtocol::new);
        testRoundTripSerialize(actualType, actualValue, expectedType, expectedValue, TCompactProtocol::new);
        testRoundTripSerialize(actualType, actualValue, expectedType, expectedValue, TFacebookCompactProtocol::new);
    }

    private <T> void testRoundTripSerialize(ThriftType actualType, T actualValue, ThriftType expectedType, T expectedValue, Function<TTransport, TProtocol> protocolFactory)
            throws Exception
    {
        // write value
        TMemoryBuffer transport = new TMemoryBuffer(10 * 1024);
        TProtocol protocol = protocolFactory.apply(transport);
        codecManager.write(actualType, actualValue, protocol);

        // read value back
        T copy = (T) codecManager.read(expectedType, protocol);
        assertNotNull(copy);

        // verify they are the same
        assertEquals(copy, expectedValue);
    }
}
