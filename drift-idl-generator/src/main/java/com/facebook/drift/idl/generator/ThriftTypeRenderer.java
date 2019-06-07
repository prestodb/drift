/*
 * Copyright (C) 2013 Facebook, Inc.
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
package com.facebook.drift.idl.generator;

import com.facebook.drift.codec.ThriftProtocolType;
import com.facebook.drift.codec.metadata.ThriftType;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

public class ThriftTypeRenderer
{
    private final Map<ThriftType, String> typeNames;

    public ThriftTypeRenderer(Map<ThriftType, String> typeNames)
    {
        this.typeNames = ImmutableMap.copyOf(typeNames);
    }

    public String toString(ThriftType type)
    {
        switch (type.getProtocolType()) {
            case ThriftProtocolType.BOOL:
                return "bool";
            case ThriftProtocolType.BYTE:
                return "byte";
            case ThriftProtocolType.DOUBLE:
                return "double";
            case ThriftProtocolType.I16:
                return "i16";
            case ThriftProtocolType.I32:
                return "i32";
            case ThriftProtocolType.I64:
                return "i64";
            case ThriftProtocolType.ENUM:
                return prefix(type) + type.getEnumMetadata().getEnumName();
            case ThriftProtocolType.MAP:
                return String.format("map<%s, %s>", toString(type.getKeyTypeReference().get()), toString(type.getValueTypeReference().get()));
            case ThriftProtocolType.SET:
                return String.format("set<%s>", toString(type.getValueTypeReference().get()));
            case ThriftProtocolType.LIST:
                return String.format("list<%s>", toString(type.getValueTypeReference().get()));
            case ThriftProtocolType.STRUCT:
                // VOID is encoded as a struct
                return type.equals(ThriftType.VOID) ? "void" : prefix(type) + type.getStructMetadata().getStructName();
            case ThriftProtocolType.STRING:
                return "string";
            case ThriftProtocolType.BINARY:
                return "binary";
        }
        throw new IllegalStateException("Bad protocol type: " + type.getProtocolType());
    }

    private String prefix(ThriftType type)
    {
        String result = typeNames.get(type);
        return (result == null) ? "" : (result + ".");
    }
}
