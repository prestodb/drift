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

import com.facebook.airlift.configuration.Config;
import com.facebook.airlift.configuration.ConfigDescription;
import com.google.common.net.HostAndPort;
import io.airlift.units.DataSize;
import io.airlift.units.Duration;
import io.airlift.units.MinDuration;

import javax.validation.constraints.Min;

import static io.airlift.units.DataSize.Unit.KILOBYTE;
import static io.airlift.units.DataSize.Unit.MEGABYTE;
import static java.util.concurrent.TimeUnit.MINUTES;

public class DriftNettyConnectionFactoryConfig
{
    private static final int DEFAULT_THREAD_COUNT = Runtime.getRuntime().availableProcessors() * 2;

    private int threadCount = DEFAULT_THREAD_COUNT;

    private boolean connectionPoolEnabled = true;
    private int connectionPoolMaxSize = 1000;
    private Duration connectionPoolIdleTimeout = new Duration(1, MINUTES);

    private Duration sslContextRefreshTime = new Duration(1, MINUTES);
    private HostAndPort socksProxy;
    private boolean nativeTransportEnabled;
    private DataSize writeLowWaterMark = new DataSize(64, KILOBYTE);
    private DataSize writeHighWaterMark = new DataSize(1, MEGABYTE);

    public int getThreadCount()
    {
        return threadCount;
    }

    @Config("thrift.client.thread-count")
    public DriftNettyConnectionFactoryConfig setThreadCount(int threadCount)
    {
        this.threadCount = threadCount;
        return this;
    }

    public boolean isConnectionPoolEnabled()
    {
        return connectionPoolEnabled;
    }

    @Config("thrift.client.connection-pool.enabled")
    public DriftNettyConnectionFactoryConfig setConnectionPoolEnabled(boolean connectionPoolEnabled)
    {
        this.connectionPoolEnabled = connectionPoolEnabled;
        return this;
    }

    @Min(1)
    public int getConnectionPoolMaxSize()
    {
        return connectionPoolMaxSize;
    }

    @Config("thrift.client.connection-pool.max-size")
    public DriftNettyConnectionFactoryConfig setConnectionPoolMaxSize(int connectionPoolMaxSize)
    {
        this.connectionPoolMaxSize = connectionPoolMaxSize;
        return this;
    }

    @MinDuration("1s")
    public Duration getConnectionPoolIdleTimeout()
    {
        return connectionPoolIdleTimeout;
    }

    @Config("thrift.client.connection-pool.idle-timeout")
    public DriftNettyConnectionFactoryConfig setConnectionPoolIdleTimeout(Duration connectionPoolIdleTimeout)
    {
        this.connectionPoolIdleTimeout = connectionPoolIdleTimeout;
        return this;
    }

    @MinDuration("1s")
    public Duration getSslContextRefreshTime()
    {
        return sslContextRefreshTime;
    }

    @Config("thrift.client.ssl-context.refresh-time")
    public DriftNettyConnectionFactoryConfig setSslContextRefreshTime(Duration sslContextRefreshTime)
    {
        this.sslContextRefreshTime = sslContextRefreshTime;
        return this;
    }

    public HostAndPort getSocksProxy()
    {
        return socksProxy;
    }

    @Config("thrift.client.socks-proxy")
    public DriftNettyConnectionFactoryConfig setSocksProxy(HostAndPort socksProxy)
    {
        this.socksProxy = socksProxy;
        return this;
    }

    public boolean isNativeTransportEnabled()
    {
        return nativeTransportEnabled;
    }

    @Config("thrift.client.native-transport.enabled")
    @ConfigDescription("Enable Native Transport")
    public DriftNettyConnectionFactoryConfig setNativeTransportEnabled(boolean nativeTransportEnabled)
    {
        this.nativeTransportEnabled = nativeTransportEnabled;
        return this;
    }

    public DataSize getWriteLowWaterMark()
    {
        return writeLowWaterMark;
    }

    @Config("thrift.client.write.low-watermark")
    @ConfigDescription("Write Low Watermark")
    public DriftNettyConnectionFactoryConfig setWriteLowWaterMark(DataSize writeLowWaterMark)
    {
        this.writeLowWaterMark = writeLowWaterMark;
        return this;
    }

    public DataSize getWriteHighWaterMark()
    {
        return writeHighWaterMark;
    }

    @Config("thrift.client.write.high-watermark")
    @ConfigDescription("Write High Watermark")
    public DriftNettyConnectionFactoryConfig setWriteHighWaterMark(DataSize writeHighWaterMark)
    {
        this.writeHighWaterMark = writeHighWaterMark;
        return this;
    }
}
