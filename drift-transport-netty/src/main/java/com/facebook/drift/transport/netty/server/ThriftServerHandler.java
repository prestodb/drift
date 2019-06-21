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
package com.facebook.drift.transport.netty.server;

import com.facebook.drift.TApplicationException;
import com.facebook.drift.codec.ThriftCodec;
import com.facebook.drift.codec.internal.ProtocolReader;
import com.facebook.drift.codec.internal.ProtocolWriter;
import com.facebook.drift.protocol.TMessage;
import com.facebook.drift.protocol.TMessageType;
import com.facebook.drift.protocol.TProtocolReader;
import com.facebook.drift.protocol.TProtocolWriter;
import com.facebook.drift.protocol.TTransport;
import com.facebook.drift.transport.MethodMetadata;
import com.facebook.drift.transport.ParameterMetadata;
import com.facebook.drift.transport.netty.codec.FrameInfo;
import com.facebook.drift.transport.netty.codec.FrameTooLargeException;
import com.facebook.drift.transport.netty.codec.Protocol;
import com.facebook.drift.transport.netty.codec.ThriftFrame;
import com.facebook.drift.transport.netty.codec.Transport;
import com.facebook.drift.transport.netty.ssl.TChannelBufferInputTransport;
import com.facebook.drift.transport.netty.ssl.TChannelBufferOutputTransport;
import com.facebook.drift.transport.server.ServerInvokeRequest;
import com.facebook.drift.transport.server.ServerMethodInvoker;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.airlift.log.Logger;
import io.airlift.units.Duration;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;

import static com.facebook.drift.TApplicationException.Type.INTERNAL_ERROR;
import static com.facebook.drift.TApplicationException.Type.INVALID_MESSAGE_TYPE;
import static com.facebook.drift.TApplicationException.Type.PROTOCOL_ERROR;
import static com.facebook.drift.TApplicationException.Type.UNKNOWN_METHOD;
import static com.facebook.drift.protocol.TMessageType.EXCEPTION;
import static com.facebook.drift.protocol.TMessageType.REPLY;
import static com.google.common.base.Defaults.defaultValue;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.regex.Pattern.CASE_INSENSITIVE;

public class ThriftServerHandler
        extends ChannelDuplexHandler
{
    private static final Logger log = Logger.get(ThriftServerHandler.class);

    private static final Pattern CONNECTION_CLOSED_MESSAGE = Pattern.compile(
            "^.*(?:connection.*(?:reset|closed|abort|broken)|broken.*pipe).*$", CASE_INSENSITIVE);

    private final ServerMethodInvoker methodInvoker;
    private final ScheduledExecutorService timeoutExecutor;
    private final Duration requestTimeout;

    public ThriftServerHandler(ServerMethodInvoker methodInvoker, Duration requestTimeout, ScheduledExecutorService timeoutExecutor)
    {
        this.methodInvoker = requireNonNull(methodInvoker, "methodInvoker is null");
        this.requestTimeout = requireNonNull(requestTimeout, "requestTimeout is null");
        this.timeoutExecutor = requireNonNull(timeoutExecutor, "timeoutExecutor is null");
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message)
    {
        if (message instanceof ThriftFrame) {
            messageReceived(context, (ThriftFrame) message);
            return;
        }
        context.fireChannelRead(message);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause)
    {
        // if possible, try to reply with an exception in case of a too large request
        if (cause instanceof FrameTooLargeException) {
            FrameTooLargeException e = (FrameTooLargeException) cause;
            // frame info may be missing in case of a large, but invalid request
            if (e.getFrameInfo().isPresent()) {
                FrameInfo frameInfo = e.getFrameInfo().get();
                try {
                    context.writeAndFlush(writeApplicationException(
                            context,
                            frameInfo.getMethodName(),
                            frameInfo.getTransport(),
                            frameInfo.getProtocol(),
                            frameInfo.getSequenceId(),
                            frameInfo.getSequenceId(),
                            frameInfo.isSupportOutOfOrderResponse(),
                            PROTOCOL_ERROR,
                            e.getMessage(),
                            e));
                }
                catch (Throwable t) {
                    context.close();
                    log.error(t, "Failed to write frame info");
                }
                return;
            }
        }

        context.close();

        // Don't log connection closed exceptions
        if (!isConnectionClosed(cause)) {
            log.error(cause);
        }
    }

    private void messageReceived(ChannelHandlerContext context, ThriftFrame frame)
    {
        TChannelBufferInputTransport inputTransport = new TChannelBufferInputTransport(frame.getMessage());
        try {
            ListenableFuture<ThriftFrame> response = decodeMessage(
                    context,
                    inputTransport,
                    frame.getTransport(),
                    frame.getProtocol(),
                    frame.getSequenceId(),
                    frame.getHeaders(),
                    frame.isSupportOutOfOrderResponse());
            Futures.addCallback(response, new FutureCallback<ThriftFrame>()
                    {
                        @Override
                        public void onSuccess(ThriftFrame result)
                        {
                            context.writeAndFlush(result);
                        }

                        @Override
                        public void onFailure(Throwable t)
                        {
                            context.disconnect();
                        }
                    },
                    directExecutor());
        }
        catch (Exception e) {
            log.error(e, "Exception processing request");
            context.disconnect();
        }
        catch (Throwable e) {
            log.error(e, "Error processing request");
            context.disconnect();
            throw e;
        }
        finally {
            inputTransport.release();
            frame.release();
        }
    }

    private ListenableFuture<ThriftFrame> decodeMessage(
            ChannelHandlerContext context,
            TTransport messageData,
            Transport transport,
            Protocol protocol,
            int frameSequenceId,
            Map<String, String> headers,
            boolean supportOutOfOrderResponse)
            throws Exception
    {
        long start = System.nanoTime();
        TProtocolReader protocolReader = protocol.createProtocol(messageData);

        TMessage message = protocolReader.readMessageBegin();
        Optional<MethodMetadata> methodMetadata = methodInvoker.getMethodMetadata(message.getName());
        if (!methodMetadata.isPresent()) {
            return immediateFuture(writeApplicationException(
                    context,
                    message.getName(),
                    transport,
                    protocol,
                    frameSequenceId,
                    message.getSequenceId(),
                    supportOutOfOrderResponse,
                    UNKNOWN_METHOD,
                    "Invalid method name: '" + message.getName() + "'",
                    null));
        }
        MethodMetadata method = methodMetadata.get();

        if (message.getType() != TMessageType.CALL && message.getType() != TMessageType.ONEWAY) {
            return immediateFuture(writeApplicationException(
                    context,
                    message.getName(),
                    transport,
                    protocol,
                    frameSequenceId,
                    message.getSequenceId(),
                    supportOutOfOrderResponse,
                    INVALID_MESSAGE_TYPE,
                    "Invalid method message type: '" + message.getType() + "'",
                    null));
        }

        Map<Short, Object> parameters = readArguments(method, protocolReader);

        ListenableFuture<Object> result = methodInvoker.invoke(new ServerInvokeRequest(method, headers, parameters));
        methodInvoker.recordResult(message.getName(), start, result);
        return FluentFuture.from(result)
                .transformAsync(
                        value -> {
                            try {
                                return immediateFuture(writeSuccessResponse(context, method, transport, protocol, frameSequenceId, message.getSequenceId(), supportOutOfOrderResponse, value));
                            }
                            catch (Exception e) {
                                return immediateFailedFuture(e);
                            }
                        },
                        directExecutor())
                .withTimeout(requestTimeout.toMillis(), MILLISECONDS, timeoutExecutor)
                .catchingAsync(
                        Exception.class,
                        exception -> {
                            try {
                                return immediateFuture(writeExceptionResponse(context, method, transport, protocol, frameSequenceId, message.getSequenceId(), supportOutOfOrderResponse, exception));
                            }
                            catch (Exception e) {
                                return immediateFailedFuture(e);
                            }
                        },
                        directExecutor());
    }

    private static Map<Short, Object> readArguments(MethodMetadata method, TProtocolReader protocol)
            throws Exception
    {
        Map<Short, Object> arguments = new HashMap<>(method.getParameters().size());
        ProtocolReader reader = new ProtocolReader(protocol);

        reader.readStructBegin();
        while (reader.nextField()) {
            short fieldId = reader.getFieldId();

            ParameterMetadata parameter = method.getParameterByFieldId(fieldId);
            if (parameter == null) {
                reader.skipFieldData();
            }
            else {
                arguments.put(fieldId, reader.readField(parameter.getCodec()));
            }
        }
        reader.readStructEnd();

        // set defaults for missing arguments
        for (ParameterMetadata parameter : method.getParameters()) {
            if (!arguments.containsKey(parameter.getFieldId())) {
                Type argumentType = parameter.getCodec().getType().getJavaType();

                Object defaultValue = null;
                if (argumentType instanceof Class) {
                    Class<?> argumentClass = (Class<?>) argumentType;
                    if (argumentClass.isPrimitive()) {
                        defaultValue = defaultValue(Primitives.unwrap(argumentClass));
                    }
                    else if (argumentClass == OptionalInt.class) {
                        defaultValue = OptionalInt.empty();
                    }
                    else if (argumentClass == OptionalLong.class) {
                        defaultValue = OptionalLong.empty();
                    }
                    else if (argumentClass == OptionalDouble.class) {
                        defaultValue = OptionalDouble.empty();
                    }
                }
                else if ((argumentType instanceof ParameterizedType) &&
                        (((ParameterizedType) argumentType).getRawType().equals(Optional.class))) {
                    defaultValue = Optional.empty();
                }

                arguments.put(parameter.getFieldId(), defaultValue);
            }
        }

        return arguments;
    }

    private static ThriftFrame writeSuccessResponse(
            ChannelHandlerContext context,
            MethodMetadata methodMetadata,
            Transport transport,
            Protocol protocol,
            int frameSequenceId,
            int messageSequenceId,
            boolean supportOutOfOrderResponse,
            Object result)
            throws Exception
    {
        TChannelBufferOutputTransport outputTransport = new TChannelBufferOutputTransport(context.alloc());
        try {
            writeResponse(
                    methodMetadata.getName(),
                    protocol.createProtocol(outputTransport),
                    messageSequenceId,
                    "success",
                    (short) 0,
                    methodMetadata.getResultCodec(),
                    result);

            return new ThriftFrame(
                    frameSequenceId,
                    outputTransport.getBuffer(),
                    ImmutableMap.of(),
                    transport,
                    protocol,
                    supportOutOfOrderResponse);
        }
        finally {
            outputTransport.release();
        }
    }

    private static ThriftFrame writeExceptionResponse(ChannelHandlerContext context,
            MethodMetadata methodMetadata,
            Transport transport,
            Protocol protocol,
            int frameSequenceId,
            int messageSequenceId,
            boolean supportOutOfOrderResponse,
            Throwable exception)
            throws Exception
    {
        Optional<Short> exceptionId = methodMetadata.getExceptionId(exception.getClass());
        if (exceptionId.isPresent()) {
            TChannelBufferOutputTransport outputTransport = new TChannelBufferOutputTransport(context.alloc());
            try {
                TProtocolWriter protocolWriter = protocol.createProtocol(outputTransport);

                writeResponse(
                        methodMetadata.getName(),
                        protocolWriter,
                        messageSequenceId,
                        "exception",
                        exceptionId.get(),
                        methodMetadata.getExceptionCodecs().get(exceptionId.get()),
                        exception);

                return new ThriftFrame(
                        frameSequenceId,
                        outputTransport.getBuffer(),
                        ImmutableMap.of(),
                        transport,
                        protocol,
                        supportOutOfOrderResponse);
            }
            finally {
                outputTransport.release();
            }
        }

        TApplicationException.Type type = INTERNAL_ERROR;
        if (exception instanceof TApplicationException) {
            type = ((TApplicationException) exception).getType().orElse(INTERNAL_ERROR);
        }
        return writeApplicationException(
                context,
                methodMetadata.getName(),
                transport,
                protocol,
                frameSequenceId,
                messageSequenceId,
                supportOutOfOrderResponse,
                type,
                "Internal error processing " + methodMetadata.getName() + ": " + exception.getMessage(),
                exception);
    }

    private static ThriftFrame writeApplicationException(
            ChannelHandlerContext context,
            String methodName,
            Transport transport,
            Protocol protocol,
            int frameSequenceId,
            int sequenceId,
            boolean supportOutOfOrderResponse,
            TApplicationException.Type errorCode,
            String errorMessage,
            Throwable cause)
            throws Exception
    {
        TApplicationException applicationException = new TApplicationException(errorCode, errorMessage);
        if (cause != null) {
            applicationException.initCause(cause);
        }

        TChannelBufferOutputTransport outputTransport = new TChannelBufferOutputTransport(context.alloc());
        try {
            TProtocolWriter protocolWriter = protocol.createProtocol(outputTransport);

            protocolWriter.writeMessageBegin(new TMessage(methodName, EXCEPTION, sequenceId));

            ExceptionWriter.writeTApplicationException(applicationException, protocolWriter);

            protocolWriter.writeMessageEnd();
            return new ThriftFrame(
                    frameSequenceId,
                    outputTransport.getBuffer(),
                    ImmutableMap.of(),
                    transport,
                    protocol,
                    supportOutOfOrderResponse);
        }
        finally {
            outputTransport.release();
        }
    }

    private static void writeResponse(
            String methodName,
            TProtocolWriter protocolWriter,
            int messageSequenceId,
            String responseFieldName,
            short responseFieldId,
            ThriftCodec<Object> responseCodec,
            Object result)
            throws Exception
    {
        protocolWriter.writeMessageBegin(new TMessage(methodName, REPLY, messageSequenceId));

        ProtocolWriter writer = new ProtocolWriter(protocolWriter);
        writer.writeStructBegin(methodName + "_result");
        writer.writeField(responseFieldName, responseFieldId, responseCodec, result);
        writer.writeStructEnd();

        protocolWriter.writeMessageEnd();
    }

    /*
     * There is no good way of detecting connection closed exception
     *
     * This implementation is a simplified version of the implementation proposed
     * in Netty: io.netty.handler.ssl.SslHandler#exceptionCaught
     *
     * This implementation ony checks a message with the regex, and doesn't do any
     * more sophisticated matching, as the regex works in most of the cases.
     */
    private boolean isConnectionClosed(Throwable t)
    {
        if (t instanceof IOException) {
            return CONNECTION_CLOSED_MESSAGE.matcher(nullToEmpty(t.getMessage())).matches();
        }
        return false;
    }
}
