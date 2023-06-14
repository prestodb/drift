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
package com.facebook.drift.transport.netty.server;

import com.facebook.drift.transport.netty.ssl.SslContextFactory;
import com.facebook.drift.transport.server.ServerMethodInvoker;
import com.facebook.drift.transport.server.ServerTransport;
import com.google.common.annotations.VisibleForTesting;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static com.facebook.airlift.concurrent.Threads.threadsNamed;
import static com.facebook.drift.transport.netty.ssl.SslContextFactory.createSslContextFactory;
import static com.google.common.base.Preconditions.checkState;
import static io.netty.channel.ChannelOption.ALLOCATOR;
import static io.netty.channel.ChannelOption.SO_BACKLOG;
import static io.netty.channel.ChannelOption.SO_KEEPALIVE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;

public class DriftNettyServerTransport
        implements ServerTransport
{
    private final ServerBootstrap bootstrap;
    private final int port;

    private final EventLoopGroup ioGroup;
    private final EventLoopGroup workerGroup;

    private Channel channel;

    private final AtomicBoolean running = new AtomicBoolean();

    public DriftNettyServerTransport(ServerMethodInvoker methodInvoker, DriftNettyServerConfig config)
    {
        this(methodInvoker, config, ByteBufAllocator.DEFAULT);
    }

    @VisibleForTesting
    public DriftNettyServerTransport(ServerMethodInvoker methodInvoker, DriftNettyServerConfig config, ByteBufAllocator allocator)
    {
        requireNonNull(methodInvoker, "methodInvoker is null");
        requireNonNull(config, "config is null");
        this.port = config.getPort();
        Class serverSocketChannelClass;
        if (config.isNativeTransportEnabled()) {
            checkState(Epoll.isAvailable(), "native transport is not available");
            ioGroup = new EpollEventLoopGroup(config.getIoThreadCount(), threadsNamed("drift-server-io-%s"));
            workerGroup = new EpollEventLoopGroup(config.getWorkerThreadCount(), threadsNamed("drift-server-worker-%s"));
            serverSocketChannelClass = EpollServerSocketChannel.class;
        }
        else if (config.isKqueueTransportEnabled()) {
            checkState(KQueue.isAvailable(), "native transport is not available");
            ioGroup = new KQueueEventLoopGroup(config.getIoThreadCount(), threadsNamed("drift-server-io-%s"));
            workerGroup = new KQueueEventLoopGroup(config.getWorkerThreadCount(), threadsNamed("drift-server-worker-%s"));
            serverSocketChannelClass = KQueueServerSocketChannel.class;
        }
        else {
            ioGroup = new NioEventLoopGroup(config.getIoThreadCount(), threadsNamed("drift-server-io-%s"));
            workerGroup = new NioEventLoopGroup(config.getWorkerThreadCount(), threadsNamed("drift-server-worker-%s"));
            serverSocketChannelClass = NioServerSocketChannel.class;
        }

        Optional<Supplier<SslContext>> sslContext = Optional.empty();
        if (config.isSslEnabled()) {
            SslContextFactory sslContextFactory = createSslContextFactory(false, config.getSslContextRefreshTime(), workerGroup);
            sslContext = Optional.of(sslContextFactory.get(
                    config.getTrustCertificate(),
                    Optional.ofNullable(config.getKey()),
                    Optional.ofNullable(config.getKey()),
                    Optional.ofNullable(config.getKeyPassword()),
                    config.getSessionCacheSize(),
                    config.getSessionTimeout(),
                    config.getCiphers()));

            // validate ssl context configuration is valid
            sslContext.get().get();
        }

        ThriftServerInitializer serverInitializer = new ThriftServerInitializer(
                methodInvoker,
                config.getMaxFrameSize(),
                config.getRequestTimeout(),
                sslContext,
                config.isAllowPlaintext(),
                config.isAssumeClientsSupportOutOfOrderResponses(),
                workerGroup);

        bootstrap = new ServerBootstrap()
                .group(ioGroup, workerGroup)
                .channel(serverSocketChannelClass)
                .childHandler(serverInitializer)
                .option(SO_BACKLOG, config.getAcceptBacklog())
                .option(ALLOCATOR, allocator)
                .childOption(SO_KEEPALIVE, true)
                .validate();
    }

    @Override
    public void start()
    {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            channel = bootstrap.bind(port).sync().channel();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while starting", e);
        }
    }

    public int getPort()
    {
        return ((InetSocketAddress) channel.localAddress()).getPort();
    }

    @Override
    public void shutdown()
    {
        try {
            if (channel != null) {
                await(channel.close());
            }
        }
        finally {
            Future<?> ioShutdown;
            try {
                ioShutdown = ioGroup.shutdownGracefully(0, 0, SECONDS);
            }
            finally {
                await(workerGroup.shutdownGracefully(0, 0, SECONDS));
            }
            await(ioShutdown);
        }
    }

    private static void await(Future<?> future)
    {
        try {
            future.await();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
