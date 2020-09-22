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

import java.net.URI;
import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

@ThriftStruct
public final class UriField
{
    @ThriftField(1)
    public URI uri;

    @ThriftConstructor
    public UriField(URI uri)
    {
        this.uri = uri;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UriField uriField = (UriField) o;
        return Objects.equals(uri, uriField.uri);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(uri);
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("uri", uri)
                .toString();
    }
}
