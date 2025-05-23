/*
 * Copyright (C) 2017 Facebook, Inc.
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
package com.facebook.drift.idl.generator;

import com.facebook.drift.codec.internal.builtin.OptionalDoubleThriftCodec;
import com.facebook.drift.codec.internal.builtin.OptionalIntThriftCodec;
import com.facebook.drift.codec.internal.builtin.OptionalLongThriftCodec;
import com.facebook.drift.codec.metadata.ThriftCatalog;
import com.facebook.drift.codec.utils.DataSizeToBytesThriftCodec;
import com.facebook.drift.codec.utils.DurationToMillisThriftCodec;
import com.facebook.drift.codec.utils.JodaDateTimeToEpochMillisThriftCodec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.function.Consumer;

import static com.google.common.io.Resources.getResource;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;

public class TestThriftIdlGenerator
{
    @Test
    public void testGenerator()
            throws Exception
    {
        assertGenerated(DriftScribe.class, "scribe", ignored -> {});
        assertGenerated(RenamedService.class, "renamed", ignored -> {});
        assertGenerated(Fruit.class, "fruit", ignored -> {});
        assertGenerated(URIField.class, "uri", ignored -> {});
        assertGenerated(TreeNode.class, "tree", ignored -> {});
        assertGenerated(OptionalField.class, "optional", ignored -> {});
        assertGenerated(CustomField.class, "custom", ignored -> {});

        assertGenerated(Point.class, "point", config -> config
                .namespaces(ImmutableMap.<String, String>builder()
                        .put("java", "com.example.thrift")
                        .put("python", "snake")
                        .build()));

        assertGenerated(OneOfEverything.class, "everything", config -> config
                .includes(ImmutableMap.<String, String>builder()
                        .put(Fruit.class.getName(), "common/fruit.thrift")
                        .build()));

        assertGenerated(UnionField.class, "union", config -> config
                .includes(ImmutableMap.<String, String>builder()
                        .put(Fruit.class.getName(), "types.thrift")
                        .build())
                .recursive(false));
    }

    private static void assertGenerated(Class<?> clazz, String name, Consumer<ThriftIdlGeneratorConfig.Builder> configConsumer)
            throws IOException
    {
        String expected = Resources.toString(getResource(format("expected/%s.txt", name)), UTF_8);

        ThriftIdlGeneratorConfig.Builder config = ThriftIdlGeneratorConfig.builder()
                .includes(ImmutableMap.of())
                .namespaces(ImmutableMap.of())
                .recursive(true);
        configConsumer.accept(config);

        ThriftIdlGenerator generator = new ThriftIdlGenerator(config.build());
        ThriftCatalog catalog = generator.getCatalog();
        generator.addCustomType(
                new DurationToMillisThriftCodec(catalog).getType(),
                new DataSizeToBytesThriftCodec(catalog).getType(),
                new JodaDateTimeToEpochMillisThriftCodec(catalog).getType(),
                new OptionalIntThriftCodec().getType(),
                new OptionalLongThriftCodec().getType(),
                new OptionalDoubleThriftCodec().getType());

        String idl = generator.generate(ImmutableList.of(clazz.getName()));

        assertEquals(idl, expected);
    }
}
