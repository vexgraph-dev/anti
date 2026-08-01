package exception;

import annotation.Draft;

/**
 * Custom runtime exception thrown when API queries, libcurl requests, or off-heap JSON transactions fail.
 */
@Draft
public class APIException extends RuntimeException
{
    public APIException(String message)
    {
        super(message);
    }

    public APIException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
