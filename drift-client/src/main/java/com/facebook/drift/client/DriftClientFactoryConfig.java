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

import com.facebook.airlift.configuration.Config;
import com.facebook.airlift.configuration.ConfigDescription;

import javax.validation.constraints.Min;

public class DriftClientFactoryConfig
{
    private int numRetryServiceThreadCount = 2 * Runtime.getRuntime().availableProcessors();

    @Min(1)
    public int getNumRetryServiceThreadCount()
    {
        return numRetryServiceThreadCount;
    }

    @Config("thrift.client.num-retry-service-thread-count")
    @ConfigDescription("Number of threads of the executor which handles part of thrift request retries")
    public DriftClientFactoryConfig setNumRetryServiceThreadCount(int numRetryServiceThreadCount)
    {
        this.numRetryServiceThreadCount = numRetryServiceThreadCount;
        return this;
    }
}
