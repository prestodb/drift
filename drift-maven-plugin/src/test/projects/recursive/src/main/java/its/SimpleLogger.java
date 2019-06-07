package its;

import com.facebook.drift.annotations.ThriftMethod;
import com.facebook.drift.annotations.ThriftService;

/**
 * Simple logging service.
 *
 * Use this for logging.
 */
@ThriftService
public interface SimpleLogger
{
    /**
     * Log a message
     *
     * @param message the string to log
     */
    @ThriftMethod
    Response log(String message);
}
