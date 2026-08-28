import java.io.File;
import java.util.ArrayList;
import java.util.Set;

public final class CommandParser {

    private CommandParser() {}
    
    public static String readCommand(Set<String> builtins) throws Exception {

        StringBuilder input = new StringBuilder();

        boolean waitingForSecondTab = false;

        while (true) {

            int c = System.in.read();

            // Enter
            if (c == '\n') {
                System.out.println();
                return input.toString();
            }

            // Ignore carriage return
            if (c == '\r') {
                continue;
            }

            // TAB
            if (c == '\t') {

                String current = input.toString();
                int lastSpace = current.lastIndexOf(' ');
                String prefix = current.substring(lastSpace + 1);

                ArrayList<String> matches =
                        findFilenameMatches(prefix);

                // No matches
                if (matches.isEmpty()) {
                    System.out.print("\007");
                    waitingForSecondTab = false;
                    continue;
                }

                // One match
                if (matches.size() == 1) {

                    String completion = matches.get(0);

                    String remaining =
                            completion.substring(prefix.length());

                    System.out.print(remaining);
                    System.out.print(" ");

                    input.append(remaining);
                    input.append(" ");

                    waitingForSecondTab = false;
                    continue;
                }

                // Multiple matches
                String commonPrefix =
                        longestCommonPrefix(matches);

                // Can extend the current input
                if (commonPrefix.length() > prefix.length()) {

                    String remaining =
                            commonPrefix.substring(prefix.length());

                    System.out.print(remaining);

                    input.append(remaining);

                    // We extended the completion,
                    // so the next TAB is considered the first TAB.
                    waitingForSecondTab = false;
                }

                // Cannot extend any further
                else {
                    if (!waitingForSecondTab) {

                        System.out.print("\007");

                        waitingForSecondTab = true;
                    }

                    // Second TAB
                    else {

                        System.out.println();

                        for (int i = 0; i < matches.size(); i++) {

                            if (i > 0) {
                                System.out.print("  ");
                            }

                            System.out.print(matches.get(i));
                        }

                        System.out.println();

                        System.out.print("$ ");
                        System.out.print(current);

                        waitingForSecondTab = false;
                    }
                }

                continue;
            }

            // Backspace
            if (c == 127 || c == 8) {

                if (input.length() > 0) {

                    input.deleteCharAt(input.length() - 1);

                    System.out.print("\b \b");
                }

                waitingForSecondTab = false;
                continue;
            }

            // Normal character
            input.append((char) c);
            System.out.print((char) c);

            // Typing anything resets the double-TAB state
            waitingForSecondTab = false;
        }
    }

    private static ArrayList<String> findFilenameMatches(
            String prefix) {

        ArrayList<String> matches = new ArrayList<>();

        File currentDirectory =
                new File(System.getProperty("user.dir"));

        File[] files = currentDirectory.listFiles();

        if (files == null) {
            return matches;
        }

        for (File file : files) {

            if (file.getName().startsWith(prefix)) {
                matches.add(file.getName());
            }
        }

        matches.sort(String::compareTo);

        return matches;
    }

    private static String longestCommonPrefix(
            ArrayList<String> strings) {

        if (strings.isEmpty()) {
            return "";
        }

        String prefix = strings.get(0);

        for (int i = 1; i < strings.size(); i++) {

            String current = strings.get(i);

            int j = 0;

            while (j < prefix.length()
                    && j < current.length()
                    && prefix.charAt(j) == current.charAt(j)) {

                j++;
            }

            prefix = prefix.substring(0, j);

            if (prefix.isEmpty()) {
                break;
            }
        }

        return prefix;
    }

    public static String[] parse(String input) {

        ArrayList<String> arguments =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean argumentStarted = false;

        for (int i = 0; i < input.length(); i++) {

            char c = input.charAt(i);

            // Single quotes
            if (inSingleQuote) {

                argumentStarted = true;

                if (c == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(c);
                }
            }

            // Double quotes
            else if (inDoubleQuote) {

                argumentStarted = true;

                switch (c) {

                    case '\\' -> {

                        if (i + 1 < input.length()) {

                            char next =
                                    input.charAt(i + 1);

                            // Only \" and \\ are special
                            if (next == '"' || next == '\\') {

                                current.append(next);
                                i++;

                            } else {

                                current.append('\\');
                            }

                        } else {

                            current.append('\\');
                        }
                    }

                    case '"' ->
                            inDoubleQuote = false;

                    default ->
                            current.append(c);
                }
            }

            // Outside quotes
            else {

                // Backslash
                if (c == '\\') {

                    argumentStarted = true;

                    if (i + 1 < input.length()) {

                        i++;

                        current.append(
                                input.charAt(i)
                        );
                    }
                }

                // Single quote
                else if (c == '\'') {

                    argumentStarted = true;
                    inSingleQuote = true;
                }

                // Double quote
                else if (c == '"') {

                    argumentStarted = true;
                    inDoubleQuote = true;
                }

                // Whitespace
                else if (Character.isWhitespace(c)) {

                    if (argumentStarted) {

                        arguments.add(
                                current.toString()
                        );

                        current.setLength(0);

                        argumentStarted = false;
                    }
                }

                // Normal character
                else {

                    argumentStarted = true;

                    current.append(c);
                }
            }
        }

        if (argumentStarted) {

            arguments.add(
                    current.toString()
            );
        }

        return arguments.toArray(String[]::new);
    }
}