package com.facebook.drift.client.exceptions;

import com.facebook.drift.transport.client.Address;
import io.airlift.units.Duration;

import java.util.Set;

public class DriftNonRetryableException
    extends DriftRetriesFailedException
{
    public DriftNonRetryableException(int invocationAttempts, Duration retryTime, int failedConnections, int overloadedRejects, Set<? extends Address> attemptedAddresses)
    {
        super("Non-retryable exception", invocationAttempts, retryTime, failedConnections, overloadedRejects, attemptedAddresses);
    }
}
