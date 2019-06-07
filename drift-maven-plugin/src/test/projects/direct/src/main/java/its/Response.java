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
