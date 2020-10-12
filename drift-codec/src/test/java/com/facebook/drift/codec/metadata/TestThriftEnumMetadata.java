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
package com.facebook.drift.codec.metadata;

import com.facebook.drift.annotations.ThriftEnum;
import com.facebook.drift.annotations.ThriftEnumUnknownValue;
import com.facebook.drift.annotations.ThriftEnumValue;
import com.facebook.drift.codec.Letter;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.Test;

import java.util.Optional;

import static org.testng.Assert.assertEquals;

public class TestThriftEnumMetadata
{
    @Test
    public void testValid()
    {
        ThriftEnumMetadata<Letter> metadata = ThriftEnumMetadataBuilder.thriftEnumMetadata(Letter.class);
        assertEquals(metadata.getEnumClass(), Letter.class);
        assertEquals(metadata.getEnumName(), "Letter");
        assertEquals(metadata.getByEnumConstant(), ImmutableMap.<Letter, Integer>builder()
                .put(Letter.A, 65)
                .put(Letter.B, 66)
                .put(Letter.C, 67)
                .put(Letter.D, 68)
                .put(Letter.UNKNOWN, -1)
                .build());
        assertEquals(metadata.getByEnumValue(), ImmutableMap.<Integer, Letter>builder()
                .put(65, Letter.A)
                .put(66, Letter.B)
                .put(67, Letter.C)
                .put(68, Letter.D)
                .put(-1, Letter.UNKNOWN)
                .build());
        assertEquals(metadata.getUnknownEnumConstant(), Optional.of(Letter.UNKNOWN));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Enum class .*MissingEnumAnnotation is not annotated with @ThriftEnum")
    public void testMissingEnumAnnotation()
    {
        ThriftEnumMetadataBuilder.thriftEnumMetadata(MissingEnumAnnotation.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Enum class .*MissingValueMethod must have a method annotated with @ThriftEnumValue")
    public void testMissingValueMethod()
    {
        ThriftEnumMetadataBuilder.thriftEnumMetadata(MissingValueMethod.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Enum class .*MultipleValueMethods has multiple methods annotated with @ThriftEnumValue")
    public void testMultipleValueMethods()
    {
        ThriftEnumMetadataBuilder.thriftEnumMetadata(MultipleValueMethods.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Enum class .*DuplicateValues returned duplicate enum values: 42")
    public void testDuplicateValues()
    {
        ThriftEnumMetadataBuilder.thriftEnumMetadata(DuplicateValues.class);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "Enum class .*MultipleUnknownValues has multiple constants annotated with @ThriftEnumUnknownValue")
    public void testMultipleUnknownValues()
    {
        ThriftEnumMetadataBuilder.thriftEnumMetadata(MultipleUnknownValues.class);
    }

    public enum MissingEnumAnnotation
    {
        FOO;

        @ThriftEnumValue
        public int value()
        {
            return 42;
        }
    }

    @ThriftEnum
    public enum MissingValueMethod
    {
        FOO
    }

    @ThriftEnum
    public enum MultipleValueMethods
    {
        FOO;

        @ThriftEnumValue
        public int value1()
        {
            return 1;
        }

        @ThriftEnumValue
        public int value2()
        {
            return 2;
        }
    }

    @ThriftEnum
    public enum DuplicateValues
    {
        FOO, BAR;

        @ThriftEnumValue
        public int value()
        {
            return 42;
        }
    }

    @ThriftEnum
    public enum MultipleUnknownValues
    {
        @ThriftEnumUnknownValue
        FOO,
        @ThriftEnumUnknownValue
        BAR;

        @ThriftEnumValue
        public int value()
        {
            return this.ordinal();
        }
    }
}
