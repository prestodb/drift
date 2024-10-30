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
package com.facebook.drift.idl.generator;

import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNullElse;

public class ThriftIdlGeneratorConfig
{
    private final String defaultPackage;
    private final Map<String, String> namespaces;
    private final Map<String, String> includes;
    private final boolean recursive;
    private final Consumer<String> errorLogger;
    private final Consumer<String> warningLogger;
    private final Consumer<String> verboseLogger;

    private ThriftIdlGeneratorConfig(
            String defaultPackage,
            Map<String, String> namespaces,
            Map<String, String> includes,
            boolean recursive,
            Consumer<String> errorLogger,
            Consumer<String> warningLogger,
            Consumer<String> verboseLogger)
    {
        this.defaultPackage = requireNonNullElse(defaultPackage, "");
        this.namespaces = ImmutableMap.copyOf(requireNonNullElse(namespaces, ImmutableMap.of()));
        this.includes = ImmutableMap.copyOf(requireNonNullElse(includes, ImmutableMap.of()));
        this.recursive = recursive;
        this.errorLogger = requireNonNullElse(errorLogger, ignored -> {});
        this.warningLogger = requireNonNullElse(warningLogger, ignored -> {});
        this.verboseLogger = requireNonNullElse(verboseLogger, ignored -> {});
    }

    public static Builder builder()
    {
        return new Builder();
    }

    public String getDefaultPackage()
    {
        return defaultPackage;
    }

    public Map<String, String> getNamespaces()
    {
        return namespaces;
    }

    public Map<String, String> getIncludes()
    {
        return includes;
    }

    public boolean isRecursive()
    {
        return recursive;
    }

    public Consumer<String> getErrorLogger()
    {
        return errorLogger;
    }

    public Consumer<String> getWarningLogger()
    {
        return warningLogger;
    }

    public Consumer<String> getVerboseLogger()
    {
        return verboseLogger;
    }

    public static class Builder
    {
        private String defaultPackage;
        private Map<String, String> namespaces;
        private Map<String, String> includes;
        private boolean recursive;
        private Consumer<String> errorLogger;
        private Consumer<String> warningLogger;
        private Consumer<String> verboseLogger;

        private Builder() {}

        public Builder defaultPackage(String defaultPackage)
        {
            this.defaultPackage = defaultPackage;
            return this;
        }

        public Builder namespaces(Map<String, String> namespaces)
        {
            this.namespaces = ImmutableMap.copyOf(requireNonNullElse(namespaces, ImmutableMap.of()));
            return this;
        }

        public Builder includes(Map<String, String> includes)
        {
            this.includes = ImmutableMap.copyOf(requireNonNullElse(includes, ImmutableMap.of()));
            return this;
        }

        public Builder recursive(boolean recursive)
        {
            this.recursive = recursive;
            return this;
        }

        public Builder errorLogger(Consumer<String> errorLogger)
        {
            this.errorLogger = errorLogger;
            return this;
        }

        public Builder warningLogger(Consumer<String> warningLogger)
        {
            this.warningLogger = warningLogger;
            return this;
        }

        public Builder verboseLogger(Consumer<String> verboseLogger)
        {
            this.verboseLogger = verboseLogger;
            return this;
        }

        public ThriftIdlGeneratorConfig build()
        {
            return new ThriftIdlGeneratorConfig(
                    defaultPackage,
                    namespaces,
                    includes,
                    recursive,
                    errorLogger,
                    warningLogger,
                    verboseLogger);
        }
    }
}
