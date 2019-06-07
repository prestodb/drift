/*
 * Copyright (C) 2017 Facebook
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
package com.facebook.drift.javadoc;

import com.facebook.drift.annotations.ThriftEnum;
import com.facebook.drift.annotations.ThriftService;
import com.facebook.drift.annotations.ThriftStruct;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestThriftAnnotations
{
    @Test
    public void testNames()
    {
        assertThat(ThriftAnnotations.THRIFT_ENUM).isEqualTo(ThriftEnum.class.getName());
        assertThat(ThriftAnnotations.THRIFT_SERVICE).isEqualTo(ThriftService.class.getName());
        assertThat(ThriftAnnotations.THRIFT_STRUCT).isEqualTo(ThriftStruct.class.getName());
    }
}
