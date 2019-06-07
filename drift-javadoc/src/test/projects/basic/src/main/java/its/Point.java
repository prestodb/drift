package its;

import com.facebook.drift.annotations.ThriftField;
import com.facebook.drift.annotations.ThriftStruct;

/**
 * Two dimensional point.
 */
@ThriftStruct
public class Point
{
    @ThriftField(1)
    public int x;

    @ThriftField(2)
    public int y;
}
