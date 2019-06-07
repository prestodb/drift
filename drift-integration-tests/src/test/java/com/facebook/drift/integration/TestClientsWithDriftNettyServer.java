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
package com.facebook.drift.integration;

import com.facebook.drift.client.MethodInvocationFilter;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.integration.scribe.drift.DriftScribeService;
import com.facebook.drift.server.DriftServer;
import com.facebook.drift.server.DriftService;
import com.facebook.drift.server.stats.NullMethodInvocationStatsFactory;
import com.facebook.drift.transport.netty.buffer.TestingPooledByteBufAllocator;
import com.facebook.drift.transport.netty.codec.Protocol;
import com.facebook.drift.transport.netty.codec.Transport;
import com.facebook.drift.transport.netty.server.DriftNettyServerConfig;
import com.facebook.drift.transport.netty.server.DriftNettyServerTransport;
import com.facebook.drift.transport.netty.server.DriftNettyServerTransportFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.net.HostAndPort;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static com.facebook.drift.integration.ApacheThriftTesterUtil.apacheThriftTestClients;
import static com.facebook.drift.integration.ClientTestUtils.DRIFT_MESSAGES;
import static com.facebook.drift.integration.ClientTestUtils.HEADER_VALUE;
import static com.facebook.drift.integration.LegacyApacheThriftTesterUtil.legacyApacheThriftTestClients;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.nCopies;
import static org.testng.Assert.assertEquals;

public class TestClientsWithDriftNettyServer
{
    private static final ThriftCodecManager CODEC_MANAGER = new ThriftCodecManager();

    @Test
    public void testDriftServer()
    {
        testDriftServer(ImmutableList.of());
    }

    @Test
    public void testHandlersWithDriftServer()
    {
        TestingFilter firstFilter = new TestingFilter();
        TestingFilter secondFilter = new TestingFilter();
        List<MethodInvocationFilter> filters = ImmutableList.of(firstFilter, secondFilter);

        int invocationCount = testDriftServer(filters);

        firstFilter.assertCounts(invocationCount);
        secondFilter.assertCounts(invocationCount);
    }

    private static int testDriftServer(List<MethodInvocationFilter> filters)
    {
        DriftScribeService scribeService = new DriftScribeService();
        AtomicInteger invocationCount = new AtomicInteger();
        AtomicInteger headerInvocationCount = new AtomicInteger();
        testDriftServer(new DriftService(scribeService), address -> {
            for (boolean secure : ImmutableList.of(true, false)) {
                for (Transport transport : ImmutableList.of(Transport.HEADER)) {
                    for (Protocol protocol : Protocol.values()) {
                        int count = Streams.concat(
                                legacyApacheThriftTestClients(filters, transport, protocol, secure).stream(),
                                DriftNettyTesterUtil.driftNettyTestClients(filters, transport, protocol, secure).stream(),
                                apacheThriftTestClients(filters, transport, protocol, secure).stream())
                                .mapToInt(client -> client.applyAsInt(address))
                                .sum();
                        invocationCount.addAndGet(count);
                        if (transport == Transport.HEADER) {
                            headerInvocationCount.addAndGet(count);
                        }
                    }
                }
            }
        });

        assertEquals(scribeService.getMessages(), newArrayList(concat(nCopies(invocationCount.get(), DRIFT_MESSAGES))));
        assertEquals(scribeService.getHeaders(), newArrayList(nCopies(headerInvocationCount.get(), HEADER_VALUE)));

        return invocationCount.get();
    }

    private static void testDriftServer(DriftService service, Consumer<HostAndPort> task)
    {
        DriftNettyServerConfig config = new DriftNettyServerConfig()
                .setSslEnabled(true)
                .setTrustCertificate(ClientTestUtils.getCertificateChainFile())
                .setKey(ClientTestUtils.getPrivateKeyFile());
        TestingPooledByteBufAllocator testingAllocator = new TestingPooledByteBufAllocator();
        DriftServer driftServer = new DriftServer(
                new DriftNettyServerTransportFactory(config, testingAllocator),
                CODEC_MANAGER,
                new NullMethodInvocationStatsFactory(),
                ImmutableSet.of(service),
                ImmutableSet.of());
        try {
            driftServer.start();

            HostAndPort address = HostAndPort.fromParts("localhost", ((DriftNettyServerTransport) driftServer.getServerTransport()).getPort());

            task.accept(address);
        }
        finally {
            driftServer.shutdown();
            testingAllocator.close();
        }
    }
}
