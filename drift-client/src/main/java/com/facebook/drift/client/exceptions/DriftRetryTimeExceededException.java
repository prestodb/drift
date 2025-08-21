package com.facebook.drift.client.exceptions;

import com.facebook.drift.transport.client.Address;
import io.airlift.units.Duration;

import java.util.Set;

import static java.lang.String.format;

public class DriftRetryTimeExceededException
    extends DriftRetriesFailedException
{
    public DriftRetryTimeExceededException(
            Duration maxRetryTime,
            int invocationAttempts,
            Duration retryTime,
            int failedConnections,
            int overloadedRejects,
            Set<? extends Address> attemptedAddresses)
    {
        super(format("Max retry time (%s) exceeded", maxRetryTime), invocationAttempts, retryTime, failedConnections, overloadedRejects, attemptedAddresses);
    }
}
