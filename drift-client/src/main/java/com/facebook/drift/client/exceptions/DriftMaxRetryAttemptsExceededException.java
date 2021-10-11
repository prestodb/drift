package com.facebook.drift.client.exceptions;

import com.facebook.drift.transport.client.Address;
import io.airlift.units.Duration;

import java.util.Set;

import static java.lang.String.format;

public class DriftMaxRetryAttemptsExceededException
    extends DriftRetriesFailedException
{
    public DriftMaxRetryAttemptsExceededException(
            int maxRetries,
            int invocationAttempts,
            Duration retryTime,
            int failedConnections,
            int overloadedRejects,
            Set<? extends Address> attemptedAddresses)
    {
        super(format("Max retry attempts (%s) exceeded", maxRetries), invocationAttempts, retryTime, failedConnections, overloadedRejects, attemptedAddresses);
    }
}
