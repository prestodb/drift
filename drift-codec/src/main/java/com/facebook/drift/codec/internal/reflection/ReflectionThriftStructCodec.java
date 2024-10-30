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
package com.facebook.drift.codec.internal.reflection;

import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.codec.internal.ProtocolReader;
import com.facebook.drift.codec.internal.ProtocolWriter;
import com.facebook.drift.codec.metadata.FieldKind;
import com.facebook.drift.codec.metadata.ThriftConstructorInjection;
import com.facebook.drift.codec.metadata.ThriftFieldInjection;
import com.facebook.drift.codec.metadata.ThriftFieldMetadata;
import com.facebook.drift.codec.metadata.ThriftInjection;
import com.facebook.drift.codec.metadata.ThriftMethodInjection;
import com.facebook.drift.codec.metadata.ThriftParameterInjection;
import com.facebook.drift.codec.metadata.ThriftStructMetadata;
import com.facebook.drift.protocol.TProtocolException;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.gaul.modernizer_maven_annotations.SuppressModernizer;

import javax.annotation.concurrent.Immutable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Throwables.throwIfInstanceOf;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.base.Verify.verify;

@Immutable
public class ReflectionThriftStructCodec<T>
        extends AbstractReflectionThriftCodec<T>
{
    public ReflectionThriftStructCodec(ThriftCodecManager manager, ThriftStructMetadata metadata)
    {
        super(manager, metadata);
    }

    @Override
    public T read(TProtocolReader protocol)
            throws Exception
    {
        ProtocolReader reader = new ProtocolReader(protocol);
        reader.readStructBegin();

        Map<Short, Object> data = new HashMap<>(metadata.getFields().size());
        while (reader.nextField()) {
            short fieldId = reader.getFieldId();

            // do we have a codec for this field
            ThriftCodec<?> codec = fields.get(fieldId);
            if (codec == null) {
                reader.skipFieldData();
                continue;
            }

            // is this field readable
            ThriftFieldMetadata field = metadata.getField(fieldId);
            if (field.isReadOnly() || field.getType() != FieldKind.THRIFT_FIELD) {
                reader.skipFieldData();
                continue;
            }

            // read the value
            Object value = reader.readField(codec);
            if (value == null) {
                if (field.getRequiredness() == ThriftField.Requiredness.REQUIRED) {
                    throw new TProtocolException("required field was not set");
                }
                else {
                    continue;
                }
            }

            data.put(fieldId, value);
        }
        reader.readStructEnd();

        // build the struct
        return constructStruct(data);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    @Override
    public void write(T instance, TProtocolWriter protocol)
            throws Exception
    {
        ProtocolWriter writer = new ProtocolWriter(protocol);
        writer.writeStructBegin(metadata.getStructName());

        for (ThriftFieldMetadata fieldMetadata : metadata.getFields(FieldKind.THRIFT_FIELD)) {
            // is the field readable?
            if (fieldMetadata.isWriteOnly()) {
                continue;
            }

            // get the field value
            Object fieldValue = getFieldValue(instance, fieldMetadata);

            // write the field
            if (fieldValue != null) {
                @SuppressWarnings("unchecked")
                ThriftCodec<Object> codec = (ThriftCodec<Object>) fields.get(fieldMetadata.getId());
                writer.writeField(fieldMetadata.getName(), fieldMetadata.getId(), codec, fieldValue);
            }
        }
        writer.writeStructEnd();
    }

    @SuppressModernizer
    @SuppressWarnings("unchecked")
    private T constructStruct(Map<Short, Object> data)
            throws Exception
    {
        // construct instance
        Object instance;
        {
            ThriftConstructorInjection constructor = metadata.getConstructorInjection().get();
            Object[] parametersValues = new Object[constructor.getParameters().size()];
            for (ThriftParameterInjection parameter : constructor.getParameters()) {
                Object value = data.get(parameter.getId());
                if (value == null) {
                    value = metadata.getField(parameter.getId()).getThriftType().getNullValue();
                }
                parametersValues[parameter.getParameterIndex()] = value;
            }

            instance = invokeConstructor(constructor.getConstructor(), parametersValues);
        }

        // inject fields
        for (ThriftFieldMetadata fieldMetadata : metadata.getFields(FieldKind.THRIFT_FIELD)) {
            for (ThriftInjection injection : fieldMetadata.getInjections()) {
                if (injection instanceof ThriftFieldInjection) {
                    ThriftFieldInjection fieldInjection = (ThriftFieldInjection) injection;
                    Object value = data.get(fieldInjection.getId());
                    if (value != null) {
                        fieldInjection.getField().set(instance, value);
                    }
                }
            }
        }

        // inject methods
        for (ThriftMethodInjection methodInjection : metadata.getMethodInjections()) {
            boolean shouldInvoke = false;
            Object[] parametersValues = new Object[methodInjection.getParameters().size()];
            for (ThriftParameterInjection parameter : methodInjection.getParameters()) {
                Object value = data.get(parameter.getId());
                if (value != null) {
                    parametersValues[parameter.getParameterIndex()] = value;
                    shouldInvoke = true;
                }
            }

            if (shouldInvoke) {
                invokeMethod(methodInjection.getMethod(), instance, parametersValues);
            }
        }

        // builder method
        if (metadata.getBuilderMethod().isPresent()) {
            ThriftMethodInjection builderMethod = metadata.getBuilderMethod().get();
            Object[] parametersValues = new Object[builderMethod.getParameters().size()];
            for (ThriftParameterInjection parameter : builderMethod.getParameters()) {
                Object value = data.get(parameter.getId());
                parametersValues[parameter.getParameterIndex()] = value;
            }

            instance = invokeMethod(builderMethod.getMethod(), instance, parametersValues);
            validateCreatedInstance(metadata.getStructClass(), instance);
        }

        return (T) instance;
    }

    static void validateCreatedInstance(Class<?> clazz, Object instance)
    {
        verify(instance != null, "Builder method returned a null instance");

        verify(
                clazz.isInstance(instance),
                "Builder method returned instance of type %s, but an instance of %s is required",
                instance.getClass().getName(),
                clazz.getName());
    }

    static <T> T invokeConstructor(Constructor<T> constructor, Object[] args)
            throws Exception
    {
        try {
            return constructor.newInstance(args);
        }
        catch (InvocationTargetException e) {
            throwIfUnchecked(e.getCause());
            throwIfInstanceOf(e.getCause(), Exception.class);
            throw e;
        }
    }

    static Object invokeMethod(Method method, Object instance, Object[] args)
            throws Exception
    {
        try {
            return method.invoke(instance, args);
        }
        catch (InvocationTargetException e) {
            throwIfUnchecked(e.getCause());
            throwIfInstanceOf(e.getCause(), Exception.class);
            throw e;
        }
    }
}
