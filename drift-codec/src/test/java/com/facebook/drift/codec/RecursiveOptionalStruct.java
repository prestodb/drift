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

import com.facebook.drift.annotations.ThriftConstructor;
import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.annotations.ThriftStruct;

import java.util.Objects;
import java.util.Optional;

import static java.util.Objects.hash;

@ThriftStruct
public class RecursiveOptionalStruct
{
    private final Optional<InnerOptionalStruct> optionalStruct;

    @ThriftConstructor
    public RecursiveOptionalStruct(Optional<InnerOptionalStruct> optionalStruct)
    {
        this.optionalStruct = optionalStruct;
    }

    @ThriftField(1)
    public Optional<InnerOptionalStruct> getOptionalStruct()
    {
        return optionalStruct;
    }

    @Override
    public int hashCode()
    {
        return hash(optionalStruct);
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == null) {
            return false;
        }

        if (!obj.getClass().equals(this.getClass())) {
            return false;
        }

        return Objects.equals(optionalStruct, ((RecursiveOptionalStruct) obj).optionalStruct);
    }

    @ThriftStruct
    public static final class InnerOptionalStruct
    {
        private final Optional<InnerOptionalStruct> optionalStruct;

        @ThriftConstructor
        public InnerOptionalStruct(Optional<InnerOptionalStruct> optionalStruct)
        {
            this.optionalStruct = optionalStruct;
        }

        @ThriftField(1)
        public Optional<InnerOptionalStruct> getOptionalStruct()
        {
            return optionalStruct;
        }

        @Override
        public int hashCode()
        {
            return hash(optionalStruct);
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj == null) {
                return false;
            }

            if (!obj.getClass().equals(this.getClass())) {
                return false;
            }

            return Objects.equals(optionalStruct, ((InnerOptionalStruct) obj).optionalStruct);
        }
    }
}
