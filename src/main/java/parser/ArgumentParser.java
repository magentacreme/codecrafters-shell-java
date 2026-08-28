package parser;

import java.util.ArrayList;
import java.util.List;

public class ArgumentParser
{
    public static List<String> parseArgs(String input)
    {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        boolean isEscaped = false;
        for (char c : input.toCharArray())
        {
            if (isEscaped && !inSingleQuotes)
            {
                current.append(c);
                isEscaped = false;
                continue;
            }

            if (c == '\\' && !inSingleQuotes)
            {
                isEscaped = true;
            }
            else if (c == '\"' && !inSingleQuotes)
            {
                inDoubleQuotes = !inDoubleQuotes;
            }
            else if (c == '\'' && !inDoubleQuotes)
            {
                inSingleQuotes = !inSingleQuotes;
            }
            else if (Character.isWhitespace(c) && !inSingleQuotes && !inDoubleQuotes)
            {
                if (!current.isEmpty())
                {
                    args.add(current.toString());
                    current.setLength(0);
                }
            }
            else
            {
                current.append(c);
            }
        }

        if (!current.isEmpty())
        {
            args.add(current.toString());
        }

        return args;
    }
}
