package exception;

public class macOSWindowException extends RuntimeException
{
    public macOSWindowException(String s)
    {
        super(s);
    }

    public macOSWindowException(String s, Throwable t)
    {
        super(s, t);
    }
}
