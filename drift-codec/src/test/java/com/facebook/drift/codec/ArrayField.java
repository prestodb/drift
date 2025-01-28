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

import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.annotations.ThriftStruct;
import com.google.common.collect.Maps;
import com.google.common.primitives.Booleans;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

@ThriftStruct("Array")
public final class ArrayField
{
    @ThriftField(1)
    public boolean[] booleanArray;

    @ThriftField(2)
    public short[] shortArray;

    @ThriftField(3)
    public int[] intArray;

    @ThriftField(4)
    public long[] longArray;

    @ThriftField(5)
    public double[] doubleArray;

    @ThriftField(6)
    public byte[] byteArray;

    @ThriftField(7)
    public float[] floatArray;

    @ThriftField(11)
    public Map<Short, boolean[]> mapBooleanArray;

    @ThriftField(12)
    public Map<Short, short[]> mapShortArray;

    @ThriftField(13)
    public Map<Short, int[]> mapIntArray;

    @ThriftField(14)
    public Map<Short, long[]> mapLongArray;

    @ThriftField(15)
    public Map<Short, double[]> mapDoubleArray;

    @ThriftField(16)
    public Map<Short, float[]> mapFloatArray;

    public ArrayField()
    {
    }

    public ArrayField(boolean[] booleanArray, short[] shortArray, int[] intArray, long[] longArray, double[] doubleArray, byte[] byteArray, float[] floatArray)
    {
        this.booleanArray = booleanArray;
        this.shortArray = shortArray;
        this.intArray = intArray;
        this.longArray = longArray;
        this.doubleArray = doubleArray;
        this.byteArray = byteArray;
        this.floatArray = floatArray;
    }

    public ArrayField(boolean[] booleanArray,
            short[] shortArray,
            int[] intArray,
            long[] longArray,
            double[] doubleArray,
            byte[] byteArray,
            float[] floatArray,
            Map<Short, boolean[]> mapBooleanArray,
            Map<Short, short[]> mapShortArray,
            Map<Short, int[]> mapIntArray,
            Map<Short, long[]> mapLongArray,
            Map<Short, double[]> mapDoubleArray,
            Map<Short, float[]> mapFloatArray)
    {
        this.booleanArray = booleanArray;
        this.shortArray = shortArray;
        this.intArray = intArray;
        this.longArray = longArray;
        this.doubleArray = doubleArray;
        this.byteArray = byteArray;
        this.floatArray = floatArray;
        this.mapBooleanArray = mapBooleanArray;
        this.mapShortArray = mapShortArray;
        this.mapIntArray = mapIntArray;
        this.mapLongArray = mapLongArray;
        this.mapDoubleArray = mapDoubleArray;
        this.mapFloatArray = mapFloatArray;
    }

    public Map<Short, List<Boolean>> getMapBooleanList()
    {
        if (mapBooleanArray == null) {
            return null;
        }
        return Maps.transformValues(mapBooleanArray, Booleans::asList);
    }

    public Map<Short, List<Short>> getMapShortList()
    {
        if (mapShortArray == null) {
            return null;
        }
        return Maps.transformValues(mapShortArray, Shorts::asList);
    }

    public Map<Short, List<Integer>> getMapIntegerList()
    {
        if (mapIntArray == null) {
            return null;
        }
        return Maps.transformValues(mapIntArray, Ints::asList);
    }

    public Map<Short, List<Long>> getMapLongList()
    {
        if (mapLongArray == null) {
            return null;
        }
        return Maps.transformValues(this.mapLongArray, Longs::asList);
    }

    public Map<Short, List<Double>> getMapDoubleList()
    {
        if (mapDoubleArray == null) {
            return null;
        }
        return Maps.transformValues(mapDoubleArray, Doubles::asList);
    }

    public Map<Short, List<Float>> getMapFloatList()
    {
        if (mapFloatArray == null) {
            return null;
        }
        return Maps.transformValues(mapFloatArray, Floats::asList);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(
                booleanArray,
                shortArray,
                intArray,
                longArray,
                doubleArray,
                byteArray,
                floatArray,
                getMapBooleanList(),
                getMapShortList(),
                getMapIntegerList(),
                getMapLongList(),
                getMapDoubleList(),
                getMapFloatList());
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ArrayField other = (ArrayField) obj;
        return Arrays.equals(this.booleanArray, other.booleanArray) &&
                Arrays.equals(this.shortArray, other.shortArray) &&
                Arrays.equals(this.intArray, other.intArray) &&
                Arrays.equals(this.longArray, other.longArray) &&
                Arrays.equals(this.doubleArray, other.doubleArray) &&
                Arrays.equals(this.byteArray, other.byteArray) &&
                Arrays.equals(this.floatArray, other.floatArray) &&
                Objects.equals(getMapBooleanList(), other.getMapBooleanList()) &&
                Objects.equals(getMapShortList(), other.getMapShortList()) &&
                Objects.equals(getMapIntegerList(), other.getMapIntegerList()) &&
                Objects.equals(getMapLongList(), other.getMapLongList()) &&
                Objects.equals(getMapDoubleList(), other.getMapDoubleList()) &&
                Objects.equals(getMapFloatList(), other.getMapFloatList());
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("booleanArray", Arrays.toString(booleanArray))
                .add("shortArray", Arrays.toString(shortArray))
                .add("intArray", Arrays.toString(intArray))
                .add("longArray", Arrays.toString(longArray))
                .add("doubleArray", Arrays.toString(doubleArray))
                .add("byteArray", Arrays.toString(byteArray))
                .add("floatArray", Arrays.toString(floatArray))
                .add("mapBooleanArray", getMapBooleanList())
                .add("mapShortArray", getMapShortList())
                .add("mapIntArray", getMapIntegerList())
                .add("mapLongArray", getMapLongList())
                .add("mapDoubleArray", getMapDoubleList())
                .add("mapFloatArray", getMapFloatList())
                .toString();
    }
}
