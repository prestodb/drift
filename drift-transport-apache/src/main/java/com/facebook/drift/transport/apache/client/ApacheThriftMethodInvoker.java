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
package com.facebook.drift.transport.apache.client;

import com.facebook.drift.TApplicationException;
import com.facebook.drift.TException;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.internal.ProtocolReader;
import com.facebook.drift.codec.internal.ProtocolWriter;
import com.facebook.drift.codec.metadata.ThriftType;
import com.facebook.drift.protocol.TProtocolException;
import com.facebook.drift.protocol.TTransportException;
import com.facebook.drift.transport.MethodMetadata;
import com.facebook.drift.transport.ParameterMetadata;
import com.facebook.drift.transport.client.Address;
import com.facebook.drift.transport.client.ConnectionFailedException;
import com.facebook.drift.transport.client.DriftApplicationException;
import com.facebook.drift.transport.client.InvokeRequest;
import com.facebook.drift.transport.client.MethodInvoker;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import io.airlift.units.Duration;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportFactory;
import org.gaul.modernizer_maven_annotations.SuppressModernizer;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Optional;

import static com.facebook.drift.TApplicationException.Type.BAD_SEQUENCE_ID;
import static com.facebook.drift.TApplicationException.Type.INVALID_MESSAGE_TYPE;
import static com.facebook.drift.TApplicationException.Type.MISSING_RESULT;
import static com.facebook.drift.TApplicationException.Type.WRONG_METHOD_NAME;
import static com.google.common.base.Throwables.throwIfUnchecked;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static java.lang.String.format;
import static java.net.Proxy.Type.SOCKS;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.thrift.protocol.TMessageType.CALL;
import static org.apache.thrift.protocol.TMessageType.EXCEPTION;
import static org.apache.thrift.protocol.TMessageType.REPLY;

public class ApacheThriftMethodInvoker
        implements MethodInvoker
{
    // This client only sends a single request per connection, so the sequence id can be constant
    private static final int SEQUENCE_ID = 77;

    private final ListeningExecutorService executorService;
    private final ListeningScheduledExecutorService delayService;
    private final TTransportFactory transportFactory;
    private final TProtocolFactory protocolFactory;

    private final int connectTimeoutMillis;
    private final int requestTimeoutMillis;
    private final Optional<HostAndPort> socksProxy;
    private final Optional<SSLContext> sslContext;

    public ApacheThriftMethodInvoker(
            ListeningExecutorService executorService,
            ListeningScheduledExecutorService delayService,
            TTransportFactory transportFactory,
            TProtocolFactory protocolFactory,
            Duration connectTimeout,
            Duration requestTimeout,
            Optional<HostAndPort> socksProxy,
            Optional<SSLContext> sslContext)
    {
        this.executorService = requireNonNull(executorService, "executorService is null");
        this.delayService = requireNonNull(delayService, "delayService is null");
        this.transportFactory = requireNonNull(transportFactory, "transportFactory is null");
        this.protocolFactory = requireNonNull(protocolFactory, "protocolFactory is null");
        this.connectTimeoutMillis = Ints.saturatedCast(requireNonNull(connectTimeout, "connectTimeout is null").toMillis());
        this.requestTimeoutMillis = Ints.saturatedCast(requireNonNull(requestTimeout, "requestTimeout is null").toMillis());
        this.socksProxy = requireNonNull(socksProxy, "socksProxy is null");
        this.sslContext = requireNonNull(sslContext, "sslContext is null");
    }

    @Override
    public ListenableFuture<Object> invoke(InvokeRequest request)
    {
        try {
            return executorService.submit(() -> invokeSynchronous(request));
        }
        catch (Exception e) {
            return immediateFailedFuture(toDriftException(e));
        }
    }

    @Override
    public ListenableFuture<?> delay(Duration duration)
    {
        try {
            return delayService.schedule(() -> null, duration.toMillis(), MILLISECONDS);
        }
        catch (Exception e) {
            return immediateFailedFuture(toDriftException(e));
        }
    }

    private Object invokeSynchronous(InvokeRequest request)
            throws Exception
    {
        Address address = request.getAddress();

        TSocket socket = createTSocket(address.getHostAndPort());
        if (!socket.isOpen()) {
            try {
                socket.open();
            }
            catch (org.apache.thrift.transport.TTransportException e) {
                throw new ConnectionFailedException(address, e);
            }
        }

        try {
            TTransport transport = transportFactory.getTransport(socket);
            TProtocol protocol = protocolFactory.getProtocol(transport);

            writeRequest(request.getMethod(), request.getParameters(), protocol);

            return readResponse(request.getMethod(), protocol);
        }
        finally {
            socket.close();
        }
    }

    @SuppressModernizer
    private TSocket createTSocket(HostAndPort address)
            throws TTransportException
    {
        Proxy proxy = socksProxy
                .map(socksAddress -> new Proxy(SOCKS, InetSocketAddress.createUnresolved(socksAddress.getHost(), socksAddress.getPort())))
                .orElse(Proxy.NO_PROXY);

        Socket socket = new Socket(proxy);
        try {
            setSocketProperties(socket);
            socket.connect(new InetSocketAddress(address.getHost(), address.getPort()), Ints.saturatedCast(connectTimeoutMillis));

            if (sslContext.isPresent()) {
                SSLContext sslContext = this.sslContext.get();

                // SSL connect is to the socks address when present
                HostAndPort sslConnectAddress = socksProxy.orElse(address);

                socket = sslContext.getSocketFactory().createSocket(socket, sslConnectAddress.getHost(), sslConnectAddress.getPort(), true);
                setSocketProperties(socket);
            }
            return new TSocket(socket);
        }
        catch (Throwable t) {
            // something went wrong, close the socket and rethrow
            try {
                socket.close();
            }
            catch (IOException e) {
                t.addSuppressed(e);
            }
            // unchecked exceptions are not transport exceptions
            // (any socket related exception will be a checked exception)
            throwIfUnchecked(t);
            throw new TTransportException(t);
        }
    }

    private void setSocketProperties(Socket socket)
            throws SocketException
    {
        socket.setSoLinger(false, 0);
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
        socket.setSoTimeout(Ints.saturatedCast(requestTimeoutMillis));
    }

    private static void writeRequest(MethodMetadata method, List<Object> parameters, TProtocol protocol)
            throws Exception
    {
        TMessage requestMessage = new TMessage(method.getName(), CALL, SEQUENCE_ID);
        protocol.writeMessageBegin(requestMessage);

        // write the parameters
        ProtocolWriter writer = new ProtocolWriter(new ThriftToDriftProtocolWriter(protocol));
        writer.writeStructBegin(method.getName() + "_args");
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i);
            ParameterMetadata parameter = method.getParameters().get(i);
            writer.writeField(parameter.getName(), parameter.getFieldId(), parameter.getCodec(), value);
        }
        writer.writeStructEnd();

        protocol.writeMessageEnd();
        protocol.getTransport().flush();
    }

    private static Object readResponse(MethodMetadata method, TProtocol responseProtocol)
            throws TException, org.apache.thrift.TException
    {
        // validate response header
        TMessage message = responseProtocol.readMessageBegin();

        if (message.type == EXCEPTION) {
            org.apache.thrift.TApplicationException exception = org.apache.thrift.TApplicationException.readFrom(responseProtocol);
            responseProtocol.readMessageEnd();
            throw exception;
        }
        if (message.type != REPLY) {
            throw new TApplicationException(INVALID_MESSAGE_TYPE, format("Received invalid message type %s from server", message.type));
        }
        if (!message.name.equals(method.getName())) {
            throw new TApplicationException(WRONG_METHOD_NAME, format("Wrong method name in reply: expected %s but received %s", method.getName(), message.name));
        }
        if (message.seqid != SEQUENCE_ID) {
            throw new TApplicationException(BAD_SEQUENCE_ID, format("%s failed: out of sequence response", method.getName()));
        }

        // read response struct
        ProtocolReader reader = new ProtocolReader(new ThriftToDriftProtocolReader(responseProtocol));
        reader.readStructBegin();

        Object results = null;
        Exception exception = null;
        try {
            while (reader.nextField()) {
                if (reader.getFieldId() == 0) {
                    results = reader.readField(method.getResultCodec());
                }
                else {
                    ThriftCodec<Object> exceptionCodec = method.getExceptionCodecs().get(reader.getFieldId());
                    if (exceptionCodec != null) {
                        exception = (Exception) reader.readField(exceptionCodec);
                    }
                    else {
                        reader.skipFieldData();
                    }
                }
            }
            reader.readStructEnd();
            responseProtocol.readMessageEnd();
        }
        catch (TException e) {
            throw e;
        }
        catch (Exception e) {
            throw new TException(e);
        }

        if (exception != null) {
            throw new DriftApplicationException(exception);
        }

        if (method.getResultCodec().getType() == ThriftType.VOID) {
            return null;
        }

        if (results == null) {
            throw new TApplicationException(MISSING_RESULT, format("%s failed: unknown result", method.getName()));
        }
        return results;
    }

    private static Exception toDriftException(Exception e)
    {
        if (e instanceof org.apache.thrift.TApplicationException) {
            org.apache.thrift.TApplicationException tae = (org.apache.thrift.TApplicationException) e;
            return new TApplicationException(tae.getType(), tae.getMessage());
        }
        if (e instanceof org.apache.thrift.transport.TTransportException) {
            return new TTransportException(e);
        }
        if (e instanceof org.apache.thrift.protocol.TProtocolException) {
            return new TProtocolException(e);
        }
        if (e instanceof org.apache.thrift.TException) {
            return new TException(e);
        }
        return e;
    }
}
