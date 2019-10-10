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
package com.facebook.drift.transport.apache.client;

import com.facebook.airlift.configuration.Config;
import com.google.common.net.HostAndPort;

public class ApacheThriftConnectionFactoryConfig
{
    private Integer threadCount;
    private HostAndPort socksProxy;

    public Integer getThreadCount()
    {
        return threadCount;
    }

    @Config("thrift.client.thread-count")
    public ApacheThriftConnectionFactoryConfig setThreadCount(Integer threadCount)
    {
        this.threadCount = threadCount;
        return this;
    }

    public HostAndPort getSocksProxy()
    {
        return socksProxy;
    }

    @Config("thrift.client.socks-proxy")
    public ApacheThriftConnectionFactoryConfig setSocksProxy(HostAndPort socksProxy)
    {
        this.socksProxy = socksProxy;
        return this;
    }
}
