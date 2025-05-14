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

import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.annotations.ThriftIdlAnnotation;
import com.facebook.drift.annotations.ThriftStruct;

import static com.facebook.drift.annotations.ThriftField.Recursiveness.TRUE;

@ThriftStruct
public class TreeNode
{
    @ThriftField(value = 1, isRecursive = TRUE, idlAnnotations = {
            @ThriftIdlAnnotation(key = "cpp.ref_type", value = "\"shared\"")})
    public TreeNode left;

    @ThriftField(value = 2, isRecursive = TRUE, idlAnnotations = {
            @ThriftIdlAnnotation(key = "cpp.ref_type", value = "\"shared\"")})
    public TreeNode right;

    @ThriftField(3)
    public String data;
}
