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
package com.facebook.drift.client;

import com.facebook.drift.client.address.AddressSelector;
import com.facebook.drift.client.stats.MethodInvocationStatsFactory;
import com.facebook.drift.client.stats.NullMethodInvocationStatsFactory;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.transport.client.MethodInvokerFactory;

import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

public class DriftClientFactoryManager<I>
{
    private final ThriftCodecManager codecManager;
    private final MethodInvokerFactory<I> methodInvokerFactory;
    private final MethodInvocationStatsFactory methodInvocationStatsFactory;
    private final Executor retryService;

    public DriftClientFactoryManager(ThriftCodecManager codecManager, MethodInvokerFactory<I> methodInvokerFactory, Executor retryService)
    {
        this(codecManager, methodInvokerFactory, new NullMethodInvocationStatsFactory(), retryService);
    }

    public DriftClientFactoryManager(ThriftCodecManager codecManager,
            MethodInvokerFactory<I> methodInvokerFactory,
            MethodInvocationStatsFactory methodInvocationStatsFactory,
            Executor retryService)
    {
        this.codecManager = requireNonNull(codecManager, "codecManager is null");
        this.methodInvokerFactory = requireNonNull(methodInvokerFactory, "methodInvokerFactory is null");
        this.methodInvocationStatsFactory = methodInvocationStatsFactory;
        this.retryService = requireNonNull(retryService, "retryService is null");
    }

    public DriftClientFactory createDriftClientFactory(I clientIdentity, AddressSelector<?> addressSelector, ExceptionClassifier exceptionClassifier)
    {
        return new DriftClientFactory(
                codecManager,
                () -> methodInvokerFactory.createMethodInvoker(clientIdentity),
                addressSelector,
                exceptionClassifier,
                methodInvocationStatsFactory,
                retryService);
    }
}
