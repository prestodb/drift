/*
 * Copyright (C) 2012 Facebook, Inc.
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
package com.facebook.drift.server;

import com.facebook.airlift.bootstrap.Bootstrap;
import com.facebook.airlift.bootstrap.LifeCycleManager;
import com.facebook.drift.TApplicationException;
import com.facebook.drift.TException;
import com.facebook.drift.annotations.ThriftException;
import com.facebook.drift.annotations.ThriftMethod;
import com.facebook.drift.annotations.ThriftService;
import com.facebook.drift.annotations.ThriftStruct;
import com.facebook.drift.codec.ThriftCodecManager;
import com.facebook.drift.server.stats.MethodInvocationStatsFactory;
import com.facebook.drift.transport.server.ServerTransportFactory;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Injector;
import org.testng.annotations.Test;

import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static com.facebook.drift.server.guice.DriftServerBinder.driftServerBinder;
import static com.google.common.util.concurrent.Futures.getDone;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestDriftServer
{
    @Test
    public void testInvoker()
            throws Exception
    {
        ResultsSupplier resultsSupplier = new ResultsSupplier();
        TestService testService = new TestService(resultsSupplier);
        TestingServerTransportFactory serverTransportFactory = new TestingServerTransportFactory();
        TestingMethodInvocationStatsFactory statsFactory = new TestingMethodInvocationStatsFactory();
        DriftServer driftServer = new DriftServer(
                serverTransportFactory,
                new ThriftCodecManager(),
                statsFactory,
                ImmutableSet.of(new DriftService(testService, Optional.empty(), true)),
                ImmutableSet.of());

        TestingServerTransport serverTransport = serverTransportFactory.getServerTransport();
        assertNotNull(serverTransport);
        assertEquals(serverTransport.getState(), TestingServerTransport.State.NOT_STARTED);

        driftServer.start();
        assertEquals(serverTransport.getState(), TestingServerTransport.State.RUNNING);

        testServer(resultsSupplier, testService, statsFactory, serverTransport);

        driftServer.shutdown();
        assertEquals(serverTransport.getState(), TestingServerTransport.State.SHUTDOWN);
    }

    @Test
    public void testFilter()
            throws Exception
    {
        ResultsSupplier resultsSupplier = new ResultsSupplier();
        PassThroughFilter passThroughFilter = new PassThroughFilter();
        ShortCircuitFilter shortCircuitFilter = new ShortCircuitFilter(resultsSupplier);
        // test servers will not see the invocation
        TestService testService = new TestService(() -> Futures.immediateFailedFuture(new Exception("Should not be called")));

        TestingServerTransportFactory serverTransportFactory = new TestingServerTransportFactory();
        TestingMethodInvocationStatsFactory statsFactory = new TestingMethodInvocationStatsFactory();
        DriftServer driftServer = new DriftServer(
                serverTransportFactory,
                new ThriftCodecManager(),
                statsFactory,
                ImmutableSet.of(new DriftService(testService, Optional.empty(), true)),
                ImmutableSet.of(passThroughFilter, shortCircuitFilter));

        TestingServerTransport serverTransport = serverTransportFactory.getServerTransport();
        assertNotNull(serverTransport);
        assertEquals(serverTransport.getState(), TestingServerTransport.State.NOT_STARTED);

        driftServer.start();
        assertEquals(serverTransport.getState(), TestingServerTransport.State.RUNNING);

        testServer(resultsSupplier, TestingInvocationTarget.combineTestingInvocationTarget(passThroughFilter, shortCircuitFilter), statsFactory, serverTransport);

        driftServer.shutdown();
        assertEquals(serverTransport.getState(), TestingServerTransport.State.SHUTDOWN);
    }

    @Test
    public void testGuiceServer()
    {
        ResultsSupplier resultsSupplier = new ResultsSupplier();
        TestService testService = new TestService(resultsSupplier);

        TestingServerTransportFactory serverTransportFactory = new TestingServerTransportFactory();
        TestingMethodInvocationStatsFactory statsFactory = new TestingMethodInvocationStatsFactory();

        Bootstrap app = new Bootstrap(
                binder -> binder.bind(TestService.class).toInstance(testService),
                binder -> driftServerBinder(binder).bindService(TestService.class),
                binder -> binder.bind(ServerTransportFactory.class).toInstance(serverTransportFactory),
                binder -> newOptionalBinder(binder, MethodInvocationStatsFactory.class)
                        .setBinding()
                        .toInstance(statsFactory));

        LifeCycleManager lifeCycleManager = null;
        try {
            Injector injector = app
                    .strictConfig()
                    .doNotInitializeLogging()
                    .initialize();
            lifeCycleManager = injector.getInstance(LifeCycleManager.class);

            assertEquals(serverTransportFactory.getServerTransport().getState(), TestingServerTransport.State.RUNNING);

            testServer(resultsSupplier, testService, statsFactory, serverTransportFactory.getServerTransport());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            if (lifeCycleManager != null) {
                try {
                    lifeCycleManager.stop();
                }
                catch (Exception ignored) {
                }
            }
        }

        assertEquals(serverTransportFactory.getServerTransport().getState(), TestingServerTransport.State.SHUTDOWN);
    }

    @Test
    public void testGuiceServerFilter()
    {
        ResultsSupplier resultsSupplier = new ResultsSupplier();
        PassThroughFilter passThroughFilter = new PassThroughFilter();
        ShortCircuitFilter shortCircuitFilter = new ShortCircuitFilter(resultsSupplier);
        // test servers will not see the invocation
        TestService testService = new TestService(() -> Futures.immediateFailedFuture(new Exception("Should not be called")));

        TestingServerTransportFactory serverTransportFactory = new TestingServerTransportFactory();
        TestingMethodInvocationStatsFactory statsFactory = new TestingMethodInvocationStatsFactory();

        Bootstrap app = new Bootstrap(
                binder -> binder.bind(TestService.class).toInstance(testService),
                binder -> driftServerBinder(binder).bindService(TestService.class),
                binder -> driftServerBinder(binder).bindFilter(passThroughFilter),
                binder -> driftServerBinder(binder).bindFilter(shortCircuitFilter),
                binder -> binder.bind(ServerTransportFactory.class).toInstance(serverTransportFactory),
                binder -> newOptionalBinder(binder, MethodInvocationStatsFactory.class)
                        .setBinding()
                        .toInstance(statsFactory));

        LifeCycleManager lifeCycleManager = null;
        try {
            Injector injector = app
                    .strictConfig()
                    .doNotInitializeLogging()
                    .initialize();
            lifeCycleManager = injector.getInstance(LifeCycleManager.class);

            assertEquals(serverTransportFactory.getServerTransport().getState(), TestingServerTransport.State.RUNNING);

            testServer(resultsSupplier, TestingInvocationTarget.combineTestingInvocationTarget(passThroughFilter, shortCircuitFilter), statsFactory, serverTransportFactory.getServerTransport());
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        finally {
            if (lifeCycleManager != null) {
                try {
                    lifeCycleManager.stop();
                }
                catch (Exception ignored) {
                }
            }
        }

        assertEquals(serverTransportFactory.getServerTransport().getState(), TestingServerTransport.State.SHUTDOWN);
    }

    private static void testServer(
            ResultsSupplier resultsSupplier,
            TestingInvocationTarget invocationTarget,
            TestingMethodInvocationStatsFactory statsFactory,
            TestingServerTransport serverTransport)
            throws ExecutionException
    {
        // test normal invocation
        assertNormalInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty());

        // test method throws TException
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new TestServiceException());
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new TException());
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new TApplicationException());
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new RuntimeException());
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new Error());

        // custom exception subclasses
        assertExceptionInvocation(resultsSupplier, serverTransport, invocationTarget, statsFactory, Optional.empty(), new TestServiceException() {});
    }

    private static void assertNormalInvocation(
            ResultsSupplier resultsSupplier,
            TestingServerTransport serverTransport,
            TestingInvocationTarget invocationTarget,
            TestingMethodInvocationStatsFactory statsFactory,
            Optional<String> qualifier)
            throws ExecutionException
    {
        TestingMethodInvocationStat testStat = statsFactory.getStat("serverService", qualifier, "test");
        testStat.clear();
        int invocationId = ThreadLocalRandom.current().nextInt();
        String expectedResult = "result " + invocationId;
        resultsSupplier.setSuccessResult(expectedResult);
        ListenableFuture<Object> result = serverTransport.invoke("test", ImmutableMap.of(), ImmutableMap.of((short) 1, invocationId, (short) 2, "normal"));
        assertTrue(result.isDone());
        assertEquals(getDone(result), expectedResult);
        invocationTarget.assertInvocation("test", invocationId, "normal");
        testStat.assertSuccess();

        TestingMethodInvocationStat testAsyncStat = statsFactory.getStat("serverService", qualifier, "testAsync");
        testAsyncStat.clear();
        invocationId = ThreadLocalRandom.current().nextInt();
        expectedResult = "async " + expectedResult;
        resultsSupplier.setSuccessResult(expectedResult);
        ListenableFuture<Object> asyncResult = serverTransport.invoke("testAsync", ImmutableMap.of(), ImmutableMap.of((short) 1, invocationId, (short) 2, "async"));
        assertTrue(asyncResult.isDone());
        assertEquals(getDone(asyncResult), expectedResult);
        invocationTarget.assertInvocation("testAsync", invocationId, "async");
        testAsyncStat.assertSuccess();
    }

    private static void assertExceptionInvocation(
            ResultsSupplier resultsSupplier,
            TestingServerTransport serverTransport,
            TestingInvocationTarget invocationTarget,
            TestingMethodInvocationStatsFactory statsFactory,
            Optional<String> qualifier,
            Throwable testException)
    {
        String name = "exception-" + testException.getClass().getName();

        TestingMethodInvocationStat testStat = statsFactory.getStat("serverService", qualifier, "test");
        testStat.clear();
        int invocationId = ThreadLocalRandom.current().nextInt();
        resultsSupplier.setFailedResult(testException);
        ListenableFuture<Object> result = serverTransport.invoke("test", ImmutableMap.of(), ImmutableMap.of((short) 1, invocationId, (short) 2, name));
        assertTrue(result.isDone());
        try {
            getDone(result);
            fail("expected exception");
        }
        catch (ExecutionException e) {
            assertSame(e.getCause(), testException);
        }
        invocationTarget.assertInvocation("test", invocationId, name);
        testStat.assertFailure();

        name = "async " + name;
        TestingMethodInvocationStat testAsyncStat = statsFactory.getStat("serverService", qualifier, "testAsync");
        testAsyncStat.clear();
        invocationId = ThreadLocalRandom.current().nextInt();
        resultsSupplier.setFailedResult(testException);
        ListenableFuture<Object> asyncResult = serverTransport.invoke("testAsync", ImmutableMap.of(), ImmutableMap.of((short) 1, invocationId, (short) 2, name));
        assertTrue(asyncResult.isDone());
        try {
            getDone(result);
            fail("expected exception");
        }
        catch (ExecutionException e) {
            assertSame(e.getCause(), testException);
        }
        invocationTarget.assertInvocation("testAsync", invocationId, name);
        testAsyncStat.assertFailure();
    }

    @ThriftService("serverService")
    public static class TestService
            implements TestingInvocationTarget
    {
        private final Supplier<ListenableFuture<Object>> resultsSupplier;

        private String methodName;
        private int id;
        private String name;

        public TestService(Supplier<ListenableFuture<Object>> resultsSupplier)
        {
            this.resultsSupplier = resultsSupplier;
        }

        @ThriftMethod
        public String test(int id, String name)
                throws TestServiceException, TException
        {
            this.methodName = "test";
            this.id = id;
            this.name = name;

            try {
                return (String) getDone(resultsSupplier.get());
            }
            catch (ExecutionException e) {
                Throwable failureResult = e.getCause();
                Throwables.propagateIfPossible(failureResult, TestServiceException.class);
                Throwables.propagateIfPossible(failureResult, TException.class);
                throw new RuntimeException(failureResult);
            }
        }

        @ThriftMethod(exception = @ThriftException(id = 0, type = TestServiceException.class))
        public ListenableFuture<String> testAsync(int id, String name)
        {
            this.methodName = "testAsync";
            this.id = id;
            this.name = name;
            return Futures.transform(resultsSupplier.get(), String::valueOf, directExecutor());
        }

        @Override
        public void assertInvocation(String expectedMethodName, int expectedId, String expectedName)
        {
            assertEquals(methodName, expectedMethodName);
            assertEquals(id, expectedId);
            assertEquals(name, expectedName);
        }
    }

    @ThriftStruct("testService")
    public static class TestServiceException
            extends Exception
    {
    }
}
