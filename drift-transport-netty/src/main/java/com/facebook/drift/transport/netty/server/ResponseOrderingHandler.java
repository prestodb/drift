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

import com.facebook.drift.transport.netty.codec.ThriftFrame;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class ResponseOrderingHandler
        extends ChannelDuplexHandler
{
    @Override
    public void channelRead(ChannelHandlerContext context, Object message)
    {
        if (message instanceof ThriftFrame) {
            ThriftFrame thriftFrame = (ThriftFrame) message;
            if (!thriftFrame.isSupportOutOfOrderResponse()) {
                context.channel().config().setAutoRead(false);
            }
        }
        context.fireChannelRead(message);
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise)
    {
        // The call to setAutoRead(true) triggers a read inline. That can trigger an entire
        // request-response cycle before we get a chance to write, breaking the ordering.
        // Write the response first so that this doesn't happen.
        context.write(message, promise);
        if (message instanceof ThriftFrame) {
            // always re-enable auto read
            context.channel().config().setAutoRead(true);
        }
    }
}
