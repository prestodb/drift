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
package com.facebook.drift.transport.netty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

/**
 * @apiNote Transformer will transfer the content of input into a newly allocated ByteBuf.
 * The old input will be de-referenced. If this is not intended, make sure pass in input.retain()
 */
interface ThriftMessageTransformer
{
    /**
     * Encode raw input into encoded message. Old input will be de-referenced.
     * Throws RuntimeException when encode failed.
     *
     * @param bufAllocator allocator to allocate output byteBuf
     * @param input raw byteBuf input
     * @return encoded message
     */
    ByteBuf transform(ByteBufAllocator bufAllocator, ByteBuf input);

    /**
     * Decode encoded input into original message. Old input will be de-referenced.
     * Throws RuntimeException when decode failed.
     *
     * @param bufAllocator allocator to allocate output byteBuf
     * @param input encoded input
     * @return decoded message
     */
    ByteBuf untransform(ByteBufAllocator bufAllocator, ByteBuf input);

    /**
     * Will try to un-transform until get at least minimumSize of byte of output or full output.
     * Old input will be de-referenced.
     *
     * @param bufAllocator allocator to allocate output byteBuf
     * @param input encoded input
     * @param minimumSize try decode at least minimumSize of byte before stop.
     * @return partially decoded message
     */
    ByteBuf tryUntransform(ByteBufAllocator bufAllocator, ByteBuf input, int minimumSize);
}
