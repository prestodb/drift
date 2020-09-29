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
package com.facebook.drift.transport.netty.client;

import com.facebook.drift.transport.client.MethodInvoker;
import com.facebook.drift.transport.client.MethodInvokerFactory;
import com.facebook.drift.transport.netty.client.ConnectionManager.ConnectionParameters;
import com.facebook.drift.transport.netty.ssl.SslContextFactory;
import com.facebook.drift.transport.netty.ssl.SslContextFactory.SslContextParameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HostAndPort;
import io.airlift.units.Duration;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import javax.annotation.PreDestroy;

import java.io.Closeable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

import static com.facebook.airlift.concurrent.Threads.daemonThreadsNamed;
import static com.facebook.drift.transport.netty.codec.Protocol.COMPACT;
import static com.facebook.drift.transport.netty.codec.Transport.HEADER;
import static com.facebook.drift.transport.netty.ssl.SslContextFactory.createSslContextFactory;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class DriftNettyMethodInvokerFactory<I>
        implements MethodInvokerFactory<I>, Closeable
{
    private final Function<I, DriftNettyClientConfig> clientConfigurationProvider;

    private final EventLoopGroup group;
    private final SslContextFactory sslContextFactory;
    private final Optional<HostAndPort> defaultSocksProxy;
    private final ConnectionFactory connectionFactory;
    private final ScheduledExecutorService connectionPoolMaintenanceExecutor;
    private final DriftNettyConnectionFactoryConfig factoryConfig;
    private final Map<Optional<I>, ConnectionPool> connectionPools = new ConcurrentHashMap<>();

    public static DriftNettyMethodInvokerFactory<?> createStaticDriftNettyMethodInvokerFactory(DriftNettyClientConfig clientConfig)
    {
        return createStaticDriftNettyMethodInvokerFactory(clientConfig, ByteBufAllocator.DEFAULT);
    }

    @VisibleForTesting
    public static DriftNettyMethodInvokerFactory<?> createStaticDriftNettyMethodInvokerFactory(DriftNettyClientConfig clientConfig, ByteBufAllocator allocator)
    {
        return new DriftNettyMethodInvokerFactory<>(new DriftNettyConnectionFactoryConfig(), clientIdentity -> clientConfig, allocator);
    }

    public DriftNettyMethodInvokerFactory(
            DriftNettyConnectionFactoryConfig factoryConfig,
            Function<I, DriftNettyClientConfig> clientConfigurationProvider)
    {
        this(factoryConfig, clientConfigurationProvider, ByteBufAllocator.DEFAULT);
    }

    @VisibleForTesting
    public DriftNettyMethodInvokerFactory(
            DriftNettyConnectionFactoryConfig factoryConfig,
            Function<I, DriftNettyClientConfig> clientConfigurationProvider,
            ByteBufAllocator allocator)
    {
        this.factoryConfig = requireNonNull(factoryConfig, "factoryConfig is null");

        if (factoryConfig.isNativeTransportEnabled()) {
            checkState(Epoll.isAvailable(), "native transport is not available");
            group = new EpollEventLoopGroup(factoryConfig.getThreadCount(), daemonThreadsNamed("drift-client-%s"));
        }
        else {
            group = new NioEventLoopGroup(factoryConfig.getThreadCount(), daemonThreadsNamed("drift-client-%s"));
        }
        this.clientConfigurationProvider = requireNonNull(clientConfigurationProvider, "clientConfigurationProvider is null");
        this.sslContextFactory = createSslContextFactory(true, factoryConfig.getSslContextRefreshTime(), group);
        this.defaultSocksProxy = Optional.ofNullable(factoryConfig.getSocksProxy());

        connectionPoolMaintenanceExecutor = newSingleThreadScheduledExecutor(daemonThreadsNamed("drift-connection-maintenance"));
        connectionFactory = new ConnectionFactory(group, sslContextFactory, allocator, factoryConfig);
    }

    @Override
    public MethodInvoker createMethodInvoker(I clientIdentity)
    {
        DriftNettyClientConfig driftNettyClientConfig = clientConfigurationProvider.apply(clientIdentity);
        ConnectionParameters clientConfig = toConnectionConfig(driftNettyClientConfig);

        // validate ssl context configuration is valid
        clientConfig.getSslContextParameters()
                .ifPresent(sslContextParameters -> sslContextFactory.get(sslContextParameters).get());

        ConnectionManager connectionManager = getConnectionManager(clientIdentity, driftNettyClientConfig);
        return new DriftNettyMethodInvoker(clientConfig, connectionManager, group);
    }

    public ConnectionManager getConnectionManager(I clientIdentity, DriftNettyClientConfig driftNettyClientConfig)
    {
        boolean connectionPoolEnabled = firstNonNull(driftNettyClientConfig.getConnectionPoolEnabled(), factoryConfig.isConnectionPoolEnabled());
        if (!connectionPoolEnabled) {
            return connectionFactory;
        }

        int connectionPoolMaxSize = firstNonNull(driftNettyClientConfig.getConnectionPoolMaxSize(), factoryConfig.getConnectionPoolMaxSize());
        int maxConnectionsPerDestination = firstNonNull(driftNettyClientConfig.getConnectionPoolMaxConnectionsPerDestination(), factoryConfig.getConnectionPoolMaxConnectionsPerDestination());
        Duration connectionPoolIdleTimeout = firstNonNull(driftNettyClientConfig.getConnectionPoolIdleTimeout(), factoryConfig.getConnectionPoolIdleTimeout());

        return connectionPools.computeIfAbsent(Optional.ofNullable(clientIdentity), ignored -> new ConnectionPool(
                connectionFactory,
                group,
                connectionPoolMaxSize,
                maxConnectionsPerDestination,
                connectionPoolIdleTimeout,
                connectionPoolMaintenanceExecutor));
    }

    @PreDestroy
    @Override
    public void close()
    {
        try {
            connectionPools.values().forEach(ConnectionPool::close);
            connectionFactory.close();
        }
        finally {
            connectionPoolMaintenanceExecutor.shutdownNow();
            try {
                group.shutdownGracefully().await();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private ConnectionParameters toConnectionConfig(DriftNettyClientConfig clientConfig)
    {
        if (clientConfig.getTransport() == HEADER && clientConfig.getProtocol() == COMPACT) {
            throw new IllegalArgumentException("HEADER transport cannot be used with COMPACT protocol, use FB_COMPACT instead");
        }

        Optional<SslContextParameters> sslContextConfig = Optional.empty();
        if (clientConfig.isSslEnabled()) {
            sslContextConfig = Optional.of(new SslContextParameters(
                    clientConfig.getTrustCertificate(),
                    Optional.ofNullable(clientConfig.getKey()),
                    Optional.ofNullable(clientConfig.getKey()),
                    Optional.ofNullable(clientConfig.getKeyPassword()),
                    clientConfig.getSessionCacheSize(),
                    clientConfig.getSessionTimeout(),
                    clientConfig.getCiphers()));
        }

        Optional<HostAndPort> socksProxy = Optional.ofNullable(clientConfig.getSocksProxy());
        if (!socksProxy.isPresent()) {
            socksProxy = defaultSocksProxy;
        }

        return new ConnectionParameters(
                clientConfig.getTransport(),
                clientConfig.getProtocol(),
                clientConfig.getMaxFrameSize(),
                clientConfig.getConnectTimeout(),
                clientConfig.getRequestTimeout(),
                socksProxy,
                sslContextConfig);
    }
}
