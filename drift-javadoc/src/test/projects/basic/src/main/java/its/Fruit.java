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

import com.facebook.drift.annotations.ThriftEnum;
import com.facebook.drift.annotations.ThriftEnumValue;

/**
 * Type of fruit
 */
@ThriftEnum
public enum Fruit
{
    /**
     * Large and sweet
     */
    APPLE(2),

    /**
     * Yellow
     */
    BANANA(3),

    /**
     * Small and tart
     */
    CHERRY(5);

    private final int id;

    Fruit(int id)
    {
        this.id = id;
    }

    @ThriftEnumValue
    public int getId()
    {
        return id;
    }
}
