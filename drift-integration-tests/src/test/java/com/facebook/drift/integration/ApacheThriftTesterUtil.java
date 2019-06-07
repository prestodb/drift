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

import com.facebook.drift.client.DriftClientFactory;
import com.facebook.drift.client.DriftClientFactoryManager;
import com.facebook.drift.client.ExceptionClassifier;
import com.facebook.drift.client.MethodInvocationFilter;
import com.facebook.drift.client.address.AddressSelector;
import com.facebook.drift.integration.scribe.drift.DriftAsyncScribe;
import com.facebook.drift.integration.scribe.drift.DriftLogEntry;
import com.facebook.drift.integration.scribe.drift.DriftScribe;
import com.facebook.drift.transport.apache.client.ApacheThriftClientConfig;
import com.facebook.drift.transport.apache.client.ApacheThriftClientModule;
import com.facebook.drift.transport.apache.client.ApacheThriftConnectionFactoryConfig;
import com.facebook.drift.transport.apache.client.ApacheThriftMethodInvokerFactory;
import com.facebook.drift.transport.client.DriftClientConfig;
import com.facebook.drift.transport.netty.codec.Protocol;
import com.facebook.drift.transport.netty.codec.Transport;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HostAndPort;

import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

import static com.facebook.drift.integration.ClientTestUtils.CODEC_MANAGER;
import static com.facebook.drift.integration.ClientTestUtils.DRIFT_MESSAGES;
import static com.facebook.drift.integration.ClientTestUtils.DRIFT_OK;
import static com.facebook.drift.integration.ClientTestUtils.HEADER_VALUE;
import static com.facebook.drift.integration.ClientTestUtils.logDriftClientBinder;
import static org.testng.Assert.assertEquals;

final class ApacheThriftTesterUtil
{
    private ApacheThriftTesterUtil() {}

    public static List<ToIntFunction<HostAndPort>> apacheThriftTestClients(List<MethodInvocationFilter> filters, Transport transport, Protocol protocol, boolean secure)
    {
        return ImmutableList.of(
                address -> logApacheThriftDriftClient(address, HEADER_VALUE, DRIFT_MESSAGES, filters, transport, protocol, secure),
                address -> logApacheThriftStaticDriftClient(address, HEADER_VALUE, DRIFT_MESSAGES, filters, transport, protocol, secure),
                address -> logApacheThriftDriftClientAsync(address, HEADER_VALUE, DRIFT_MESSAGES, filters, transport, protocol, secure),
                address -> logApacheThriftClientBinder(address, HEADER_VALUE, DRIFT_MESSAGES, filters, transport, protocol, secure));
    }

    private static int logApacheThriftDriftClient(
            HostAndPort address,
            String headerValue,
            List<DriftLogEntry> entries,
            List<MethodInvocationFilter> filters,
            Transport transport,
            Protocol protocol,
            boolean secure)
    {
        if (!isValidConfiguration(transport, protocol)) {
            return 0;
        }

        AddressSelector<?> addressSelector = context -> Optional.of(() -> address);
        ApacheThriftClientConfig config = new ApacheThriftClientConfig()
                .setTransport(toApacheThriftTransport(transport))
                .setProtocol(toApacheThriftProtocol(protocol))
                .setTrustCertificate(ClientTestUtils.getCertificateChainFile())
                .setSslEnabled(secure);
        ApacheThriftConnectionFactoryConfig factoryConfig = new ApacheThriftConnectionFactoryConfig();
        try (ApacheThriftMethodInvokerFactory<String> methodInvokerFactory = new ApacheThriftMethodInvokerFactory<>(factoryConfig, clientIdentity -> config)) {
            DriftClientFactoryManager<String> clientFactoryManager = new DriftClientFactoryManager<>(CODEC_MANAGER, methodInvokerFactory);
            DriftClientFactory proxyFactory = clientFactoryManager.createDriftClientFactory("clientIdentity", addressSelector, ExceptionClassifier.NORMAL_RESULT);

            DriftScribe scribe = proxyFactory.createDriftClient(DriftScribe.class, Optional.empty(), filters, new DriftClientConfig()).get();

            assertEquals(scribe.log(headerValue, entries), DRIFT_OK);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 1;
    }

    private static int logApacheThriftStaticDriftClient(
            HostAndPort address,
            String headerValue,
            List<DriftLogEntry> entries,
            List<MethodInvocationFilter> filters,
            Transport transport,
            Protocol protocol,
            boolean secure)
    {
        if (!isValidConfiguration(transport, protocol)) {
            return 0;
        }

        AddressSelector<?> addressSelector = context -> Optional.of(() -> address);
        ApacheThriftClientConfig config = new ApacheThriftClientConfig()
                .setTransport(toApacheThriftTransport(transport))
                .setProtocol(toApacheThriftProtocol(protocol))
                .setTrustCertificate(ClientTestUtils.getCertificateChainFile())
                .setSslEnabled(secure);

        try (ApacheThriftMethodInvokerFactory<?> methodInvokerFactory = ApacheThriftMethodInvokerFactory.createStaticApacheThriftMethodInvokerFactory(config)) {
            DriftClientFactory proxyFactory = new DriftClientFactory(CODEC_MANAGER, methodInvokerFactory, addressSelector);

            DriftScribe scribe = proxyFactory.createDriftClient(DriftScribe.class, Optional.empty(), filters, new DriftClientConfig()).get();

            assertEquals(scribe.log(headerValue, entries), DRIFT_OK);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 1;
    }

    private static int logApacheThriftDriftClientAsync(
            HostAndPort address,
            String headerValue,
            List<DriftLogEntry> entries,
            List<MethodInvocationFilter> filters,
            Transport transport,
            Protocol protocol,
            boolean secure)
    {
        if (!isValidConfiguration(transport, protocol)) {
            return 0;
        }

        AddressSelector<?> addressSelector = context -> Optional.of(() -> address);
        ApacheThriftClientConfig config = new ApacheThriftClientConfig()
                .setTransport(toApacheThriftTransport(transport))
                .setProtocol(toApacheThriftProtocol(protocol))
                .setTrustCertificate(ClientTestUtils.getCertificateChainFile())
                .setSslEnabled(secure);
        ApacheThriftConnectionFactoryConfig factoryConfig = new ApacheThriftConnectionFactoryConfig();
        try (ApacheThriftMethodInvokerFactory<String> methodInvokerFactory = new ApacheThriftMethodInvokerFactory<>(factoryConfig, clientIdentity -> config)) {
            DriftClientFactoryManager<String> proxyFactoryManager = new DriftClientFactoryManager<>(CODEC_MANAGER, methodInvokerFactory);
            DriftClientFactory proxyFactory = proxyFactoryManager.createDriftClientFactory("myFactory", addressSelector, ExceptionClassifier.NORMAL_RESULT);

            DriftAsyncScribe scribe = proxyFactory.createDriftClient(DriftAsyncScribe.class, Optional.empty(), filters, new DriftClientConfig()).get();

            assertEquals(scribe.log(headerValue, entries).get(), DRIFT_OK);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return 1;
    }

    private static int logApacheThriftClientBinder(
            HostAndPort address,
            String headerValue,
            List<DriftLogEntry> entries,
            List<MethodInvocationFilter> filters,
            Transport transport,
            Protocol protocol,
            boolean secure)
    {
        if (!isValidConfiguration(transport, protocol)) {
            return 0;
        }

        return logDriftClientBinder(address, headerValue, entries, new ApacheThriftClientModule(), filters, transport, protocol, secure);
    }

    private static boolean isValidConfiguration(Transport transport, Protocol protocol)
    {
        // Apache thrift client does not support header protocol
        return transport != Transport.HEADER && protocol != Protocol.FB_COMPACT;
    }

    private static ApacheThriftClientConfig.Transport toApacheThriftTransport(Transport transport)
    {
        switch (transport) {
            case UNFRAMED:
                return ApacheThriftClientConfig.Transport.UNFRAMED;
            case FRAMED:
                return ApacheThriftClientConfig.Transport.FRAMED;
            default:
                throw new IllegalArgumentException("Unsupported transport " + transport);
        }
    }

    private static ApacheThriftClientConfig.Protocol toApacheThriftProtocol(Protocol protocol)
    {
        switch (protocol) {
            case BINARY:
                return ApacheThriftClientConfig.Protocol.BINARY;
            case COMPACT:
                return ApacheThriftClientConfig.Protocol.COMPACT;
            default:
                throw new IllegalArgumentException("Unsupported protocol " + protocol);
        }
    }
}
