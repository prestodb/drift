/*
 * Copyright (C) 2017 Facebook, Inc.
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
package com.facebook.drift.client.exceptions;

import com.facebook.drift.transport.client.Address;
import com.google.common.collect.ImmutableSet;
import io.airlift.units.Duration;

import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class DriftRetriesFailedException
        extends Exception
{
    private final int invocationAttempts;
    private final int failedConnections;
    private final Duration retryTime;
    private final int overloadedRejects;
    private final Set<? extends Address> attemptedAddresses;

    DriftRetriesFailedException(
            String reason,
            int invocationAttempts,
            Duration retryTime,
            int failedConnections,
            int overloadedRejects,
            Set<? extends Address> attemptedAddresses)
    {
        super(format(
                "%s (invocationAttempts: %s, duration: %s, failedConnections: %s, overloadedRejects: %s, attemptedAddresses: %s)",
                reason,
                invocationAttempts,
                retryTime,
                failedConnections,
                overloadedRejects,
                attemptedAddresses));
        this.invocationAttempts = invocationAttempts;
        this.failedConnections = failedConnections;
        this.retryTime = requireNonNull(retryTime, "retryTime is null");
        this.overloadedRejects = overloadedRejects;
        this.attemptedAddresses = ImmutableSet.copyOf(requireNonNull(attemptedAddresses, "attemptedAddresses is null"));
    }

    public int getInvocationAttempts()
    {
        return invocationAttempts;
    }

    public int getFailedConnections()
    {
        return failedConnections;
    }

    public Duration getRetryTime()
    {
        return retryTime;
    }

    public int getOverloadedRejects()
    {
        return overloadedRejects;
    }

    public Set<? extends Address> getAttemptedAddresses()
    {
        return attemptedAddresses;
    }
}
