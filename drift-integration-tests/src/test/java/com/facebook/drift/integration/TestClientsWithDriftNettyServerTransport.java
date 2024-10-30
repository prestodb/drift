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
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.integration.scribe.apache.LogEntry;
import com.facebook.drift.integration.scribe.drift.DriftLogEntry;
import com.facebook.drift.integration.scribe.drift.DriftResultCode;
import com.facebook.drift.transport.MethodMetadata;
import com.facebook.drift.transport.ParameterMetadata;
import com.facebook.drift.transport.netty.buffer.TestingPooledByteBufAllocator;
import com.facebook.drift.transport.netty.codec.Protocol;
import com.facebook.drift.transport.netty.codec.Transport;
import com.facebook.drift.transport.netty.server.DriftNettyServerConfig;
import com.facebook.drift.transport.netty.server.DriftNettyServerTransport;
import com.facebook.drift.transport.netty.server.DriftNettyServerTransportFactory;
import com.facebook.drift.transport.server.ServerInvokeRequest;
import com.facebook.drift.transport.server.ServerMethodInvoker;
import com.facebook.drift.transport.server.ServerTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

import static com.facebook.drift.codec.metadata.ThriftType.list;
import static com.facebook.drift.integration.ApacheThriftTesterUtil.apacheThriftTestClients;
import static com.facebook.drift.integration.ClientTestUtils.DRIFT_OK;
import static com.facebook.drift.integration.ClientTestUtils.MESSAGES;
import static com.facebook.drift.integration.DriftNettyTesterUtil.driftNettyTestClients;
import static com.facebook.drift.integration.LegacyApacheThriftTesterUtil.legacyApacheThriftTestClients;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.MoreCollectors.onlyElement;
import static com.google.common.collect.Streams.concat;
import static java.util.Collections.nCopies;
import static org.testng.Assert.assertEquals;

public class TestClientsWithDriftNettyServerTransport
{
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
        TestServerMethodInvoker methodInvoker = new TestServerMethodInvoker();

        ImmutableList.Builder<ToIntFunction<HostAndPort>> clients = ImmutableList.builder();
        for (boolean secure : ImmutableList.of(true, false)) {
            for (Transport transport : Transport.values()) {
                for (Protocol protocol : Protocol.values()) {
                    clients.addAll(legacyApacheThriftTestClients(filters, transport, protocol, secure))
                            .addAll(driftNettyTestClients(filters, transport, protocol, secure))
                            .addAll(apacheThriftTestClients(filters, transport, protocol, secure));
                }
            }
        }
        int invocationCount = testDriftServer(methodInvoker, clients.build());

        assertEquals(methodInvoker.getMessages(), concat(nCopies(invocationCount, MESSAGES).stream()).flatMap(List::stream).collect(toImmutableList()));

        return invocationCount;
    }

    private static int testDriftServer(ServerMethodInvoker methodInvoker, List<ToIntFunction<HostAndPort>> clients)
    {
        DriftNettyServerConfig config = new DriftNettyServerConfig()
                .setSslEnabled(true)
                .setTrustCertificate(ClientTestUtils.getCertificateChainFile())
                .setKey(ClientTestUtils.getPrivateKeyFile());
        TestingPooledByteBufAllocator testingAllocator = new TestingPooledByteBufAllocator();
        ServerTransport serverTransport = new DriftNettyServerTransportFactory(config, testingAllocator).createServerTransport(methodInvoker);
        try {
            serverTransport.start();

            HostAndPort address = HostAndPort.fromParts("localhost", ((DriftNettyServerTransport) serverTransport).getPort());

            int sum = 0;
            for (ToIntFunction<HostAndPort> client : clients) {
                sum += client.applyAsInt(address);
            }
            return sum;
        }
        finally {
            serverTransport.shutdown();
            testingAllocator.close();
        }
    }

    private static final ThriftCodecManager CODEC_MANAGER = new ThriftCodecManager();
    private static final MethodMetadata LOG_METHOD_METADATA = new MethodMetadata(
            "Log",
            ImmutableList.of(new ParameterMetadata(
                    (short) 1,
                    "messages",
                    (ThriftCodec<Object>) CODEC_MANAGER.getCodec(list(CODEC_MANAGER.getCodec(DriftLogEntry.class).getType())))),
            (ThriftCodec<Object>) (Object) CODEC_MANAGER.getCodec(DriftResultCode.class),
            ImmutableMap.of(),
            false,
            true);

    private static class TestServerMethodInvoker
            implements ServerMethodInvoker
    {
        private final List<LogEntry> messages = new CopyOnWriteArrayList<>();

        private List<LogEntry> getMessages()
        {
            return messages;
        }

        @Override
        public Optional<MethodMetadata> getMethodMetadata(String name)
        {
            if (LOG_METHOD_METADATA.getName().equals(name)) {
                return Optional.of(LOG_METHOD_METADATA);
            }
            return Optional.empty();
        }

        @Override
        public ListenableFuture<Object> invoke(ServerInvokeRequest request)
        {
            MethodMetadata method = request.getMethod();
            if (!LOG_METHOD_METADATA.getName().equals(method.getName())) {
                return Futures.immediateFailedFuture(new IllegalArgumentException("unknown method " + method));
            }

            Map<Short, Object> parameters = request.getParameters();
            if (parameters.size() != 1 || !parameters.containsKey((short) 1) || !(parameters.values().stream().collect(onlyElement()) instanceof List)) {
                return Futures.immediateFailedFuture(new IllegalArgumentException("invalid parameters"));
            }
            ((List<DriftLogEntry>) parameters.values()
                    .stream()
                    .collect(onlyElement()))
                    .forEach(driftLogEntry -> {
                        messages.add(new LogEntry(driftLogEntry.getCategory(), driftLogEntry.getMessage()));
                    });

            return Futures.immediateFuture(DRIFT_OK);
        }

        @Override
        public void recordResult(String methodName, long startTime, ListenableFuture<Object> result)
        {
            // TODO implement
        }
    }
}
