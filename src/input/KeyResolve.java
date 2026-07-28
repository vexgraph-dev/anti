package input;

public class KeyResolve
{
    KeyAction down;
    KeyAction up;
    KeyAction click;
    KeyAction doubleClick;
    KeyAction tripleClick;

    @FunctionalInterface
    private interface KeyAction
    {
        long run(int keyCode);
    }

}
