package autocomplete;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jline.reader.Completer;

public class AutoCompleteRegistry
{
    private final Map<String, Completer> completers = new HashMap<>();

    public void register(String key, Completer completer)
    {
        this.completers.put(key, completer);
    }

    public Optional<Completer> get(String key)
    {
        return Optional.ofNullable(completers.get(key));
    }

    public void remove(String key)
    {
        completers.remove(key);
    }
}
