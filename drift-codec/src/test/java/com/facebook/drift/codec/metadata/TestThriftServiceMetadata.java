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
package com.facebook.drift.codec.metadata;

import com.facebook.drift.annotations.ThriftMethod;
import com.facebook.drift.annotations.ThriftOrder;
import com.facebook.drift.annotations.ThriftService;
import com.google.common.collect.ImmutableList;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.assertj.core.api.Assertions.assertThat;

public class TestThriftServiceMetadata
{
    private ThriftCatalog catalog;

    @BeforeClass
    public void setUp()
    {
        catalog = new ThriftCatalog();
    }

    @AfterClass(alwaysRun = true)
    public void tearDown()
    {
        catalog = null;
    }

    @Test
    public void testMethodOrder()
            throws Exception
    {
        assertMethodOrder(Service1.class, ImmutableList.of());
        assertMethodOrder(
                Service2.class,
                ImmutableList.of(
                        Service2.class.getMethod("method1", String.class)));
        assertMethodOrder(
                Service3.class,
                ImmutableList.of(
                        Service3.class.getMethod("method1", String.class),
                        Service3.class.getMethod("method2", String.class)));
        assertMethodOrder(
                Service4.class,
                ImmutableList.of(
                        Service4.class.getMethod("method1", String.class),
                        Service4.class.getMethod("method2", String.class)));
        assertMethodOrder(
                Service5.class,
                ImmutableList.of(
                        Service5.class.getMethod("method3", String.class),
                        Service5.class.getMethod("method1", String.class),
                        Service5.class.getMethod("method2", String.class)));
        assertMethodOrder(
                Service6.class,
                ImmutableList.of(
                        Service6.class.getMethod("method3", String.class),
                        Service6.class.getMethod("method5", String.class),
                        Service6.class.getMethod("method1", String.class),
                        Service6.class.getMethod("method2", String.class),
                        Service6.class.getMethod("method4", String.class)));
        assertMethodOrder(
                Service7.class,
                ImmutableList.of(
                        Service7.class.getMethod("method5", String.class),
                        Service7.class.getMethod("method3", String.class),
                        Service7.class.getMethod("method1", String.class),
                        Service7.class.getMethod("method2", String.class),
                        Service7.class.getMethod("method4", String.class)));
        assertMethodOrder(
                Service8.class,
                ImmutableList.of(
                        Service8.class.getMethod("method", String.class),
                        Service8.class.getMethod("method", String.class, String.class)));
        assertMethodOrder(
                Service9.class,
                ImmutableList.of(
                        Service9.class.getMethod("method", String.class, String.class, String.class),
                        Service9.class.getMethod("method", String.class),
                        Service9.class.getMethod("method", String.class, String.class)));
    }

    private void assertMethodOrder(Class<?> clazz, List<Method> expected)
    {
        ThriftServiceMetadata metadata = new ThriftServiceMetadata(clazz, catalog);
        List<Method> actual = metadata.getMethods().stream()
                .map(ThriftMethodMetadata::getMethod)
                .collect(toImmutableList());
        assertThat(actual).isEqualTo(expected);
    }

    @ThriftService
    public interface Service1
    {
    }

    @ThriftService
    public interface Service2
    {
        @ThriftMethod
        String method1(String parameter);
    }

    @ThriftService
    public interface Service3
    {
        @ThriftMethod
        String method1(String parameter);

        @ThriftMethod
        String method2(String parameter);
    }

    @ThriftService
    public interface Service4
    {
        @ThriftMethod("method")
        String method1(String parameter);

        @ThriftMethod("method")
        String method2(String parameter);
    }

    @ThriftService
    public interface Service5
    {
        @ThriftMethod("method1")
        String method1(String parameter);

        @ThriftMethod("method2")
        String method2(String parameter);

        @ThriftOrder(0)
        @ThriftMethod("method3")
        String method3(String parameter);
    }

    @ThriftService
    public interface Service6
    {
        @ThriftMethod("method1")
        String method1(String parameter);

        @ThriftMethod("method2")
        String method2(String parameter);

        @ThriftOrder(0)
        @ThriftMethod("method3")
        String method3(String parameter);

        @ThriftMethod("method4")
        String method4(String parameter);

        @ThriftOrder(0)
        @ThriftMethod("method5")
        String method5(String parameter);
    }

    @ThriftService
    public interface Service7
    {
        @ThriftMethod("method1")
        String method1(String parameter);

        @ThriftMethod("method2")
        String method2(String parameter);

        @ThriftOrder(1)
        @ThriftMethod("method3")
        String method3(String parameter);

        @ThriftMethod("method4")
        String method4(String parameter);

        @ThriftOrder(0)
        @ThriftMethod("method5")
        String method5(String parameter);
    }

    @ThriftService
    public interface Service8
    {
        @ThriftMethod
        String method(String parameter);

        @ThriftMethod
        String method(String parameter1, String parameter2);
    }

    @ThriftService
    public interface Service9
    {
        @ThriftMethod
        String method(String parameter);

        @ThriftMethod
        String method(String parameter1, String parameter2);

        @ThriftOrder(0)
        @ThriftMethod
        String method(String parameter1, String parameter2, String parameter3);
    }
}
