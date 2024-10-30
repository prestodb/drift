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

import com.facebook.drift.annotations.ThriftMethod;
import com.facebook.drift.annotations.ThriftService;
import com.google.common.collect.ComparisonChain;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.concurrent.Immutable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Comparators.emptiesLast;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@Immutable
public class ThriftServiceMetadata
{
    private final String name;
    private final String idlName;
    private final List<ThriftMethodMetadata> methods;
    private final List<String> documentation;

    public ThriftServiceMetadata(Class<?> serviceClass, ThriftCatalog catalog)
    {
        requireNonNull(serviceClass, "serviceClass is null");
        ThriftService thriftService = getThriftServiceAnnotation(serviceClass);

        if (thriftService.value().isEmpty()) {
            name = serviceClass.getSimpleName();
        }
        else {
            name = thriftService.value();
        }

        if (thriftService.idlName().isEmpty()) {
            idlName = name;
        }
        else {
            idlName = thriftService.idlName();
        }

        List<OrderedThriftMethodMetadata> methods = new ArrayList<>();
        for (Method method : ReflectionHelper.findAnnotatedMethods(serviceClass, ThriftMethod.class)) {
            if (method.isAnnotationPresent(ThriftMethod.class)) {
                methods.add(new OrderedThriftMethodMetadata(new ThriftMethodMetadata(method, catalog), ThriftCatalog.getMethodOrder(method)));
            }
        }
        methods.sort(null);
        this.methods = methods.stream()
                .map(OrderedThriftMethodMetadata::getThriftMethodMetadata)
                .collect(toImmutableList());

        documentation = ThriftCatalog.getThriftDocumentation(serviceClass);
    }

    public String getName()
    {
        return name;
    }

    public String getIdlName()
    {
        return idlName;
    }

    @SuppressFBWarnings("EI_EXPOSE_REP")
    public List<ThriftMethodMetadata> getMethods()
    {
        return methods;
    }

    public List<String> getDocumentation()
    {
        return documentation;
    }

    public static ThriftService getThriftServiceAnnotation(Class<?> serviceClass)
    {
        Set<ThriftService> serviceAnnotations = ReflectionHelper.getEffectiveClassAnnotations(serviceClass, ThriftService.class);
        checkArgument(!serviceAnnotations.isEmpty(), "Service class %s is not annotated with @ThriftService", serviceClass.getName());
        checkArgument(serviceAnnotations.size() == 1, "Service class %s has multiple conflicting @ThriftService annotations: %s", serviceClass.getName(), serviceAnnotations);

        return serviceAnnotations.stream().collect(onlyElement());
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
        ThriftServiceMetadata that = (ThriftServiceMetadata) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(methods, that.methods);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(name, methods);
    }

    private static class OrderedThriftMethodMetadata
            implements Comparable<OrderedThriftMethodMetadata>
    {
        private final ThriftMethodMetadata thriftMethodMetadata;
        private final Optional<Integer> order;

        private OrderedThriftMethodMetadata(ThriftMethodMetadata thriftMethodMetadata, Optional<Integer> order)
        {
            this.thriftMethodMetadata = requireNonNull(thriftMethodMetadata, "thriftMethodMetadata is null");
            this.order = requireNonNull(order, "order is null");
        }

        public ThriftMethodMetadata getThriftMethodMetadata()
        {
            return thriftMethodMetadata;
        }

        public Optional<Integer> getOrder()
        {
            return order;
        }

        @Override
        public int compareTo(OrderedThriftMethodMetadata that)
        {
            return ComparisonChain.start()
                    .compare(this.order, that.order, emptiesLast(Integer::compareTo))
                    .compare(this.thriftMethodMetadata.getName(), that.thriftMethodMetadata.getName())
                    .compare(this.thriftMethodMetadata.getMethod().getName(), that.thriftMethodMetadata.getMethod().getName())
                    .compare(this.thriftMethodMetadata.getMethod().getParameterTypes(), that.thriftMethodMetadata.getMethod().getParameterTypes(), (left, right) -> {
                        String leftParameterClassNames = Arrays.stream(left)
                                .map(Class::getName)
                                .collect(joining(","));
                        String rightParameterClassNames = Arrays.stream(right)
                                .map(Class::getName)
                                .collect(joining(","));
                        return leftParameterClassNames.compareTo(rightParameterClassNames);
                    })
                    .result();
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
            OrderedThriftMethodMetadata that = (OrderedThriftMethodMetadata) o;
            return Objects.equals(thriftMethodMetadata, that.thriftMethodMetadata) &&
                    Objects.equals(order, that.order);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(thriftMethodMetadata, order);
        }
    }
}
