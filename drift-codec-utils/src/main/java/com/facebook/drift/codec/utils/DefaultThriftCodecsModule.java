/*
 * Copyright (C) 2020 Facebook, Inc.
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
package com.facebook.drift.codec.utils;

import com.google.inject.Binder;
import com.google.inject.Module;

import static com.facebook.drift.codec.guice.ThriftCodecBinder.thriftCodecBinder;

public class DefaultThriftCodecsModule
        implements Module
{
    @Override
    public void configure(Binder binder)
    {
        thriftCodecBinder(binder)
                .bindCustomThriftCodec(LocaleToLanguageTagCodec.class);
        thriftCodecBinder(binder)
                .bindCustomThriftCodec(DurationToMillisThriftCodec.class);
        thriftCodecBinder(binder)
                .bindCustomThriftCodec(DataSizeToBytesThriftCodec.class);
        thriftCodecBinder(binder)
                .bindCustomThriftCodec(JodaDateTimeToEpochMillisThriftCodec.class);
        thriftCodecBinder(binder)
                .bindCustomThriftCodec(UuidToLeachSalzBinaryEncodingThriftCodec.class);
    }
}
