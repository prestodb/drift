package com.facebook.drift.client.exceptions;

import com.facebook.drift.transport.client.Address;
import io.airlift.units.Duration;

import java.util.Set;

public class DriftNoHostsAvailableException
    extends DriftRetriesFailedException
{
    public DriftNoHostsAvailableException(int invocationAttempts, Duration retryTime, int failedConnections, int overloadedRejects, Set<? extends Address> attemptedAddresses)
    {
        super("No hosts available", invocationAttempts, retryTime, failedConnections, overloadedRejects, attemptedAddresses);
    }
}
