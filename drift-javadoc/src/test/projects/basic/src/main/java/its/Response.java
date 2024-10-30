/*
 * Copyright (C) 2012 ${project.organization.name}
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
package its;

import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.annotations.ThriftStruct;

@ThriftStruct
public class Response
{
    private int type;

    /**
     * Get response type
     */
    @ThriftField(1)
    public int getType()
    {
        return type;
    }

    /**
     * Set response type
     */
    @ThriftField
    public void setType(int type)
    {
        this.type = type;
    }
}
