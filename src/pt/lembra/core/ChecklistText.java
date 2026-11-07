package pt.lembra.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converte texto colado numa lista de itens: uma linha = um item.
 * Remove marcadores comuns ("- ", "• ", "1. ", "[ ] "…) e reconhece itens já feitos ("[x]", "☑", "✓").
 */
public final class ChecklistText {
    private ChecklistText() {}

    public static final class Item {
        public final String text; public final boolean done;
        Item(String t, boolean d) { text = t; done = d; }
    }

    private static final Pattern DONE = Pattern.compile("^\\s*(?:[-*•]\\s*)?(?:\\[[xX✓✔]\\]|☑|✅|✔|✓)\\s*");
    private static final Pattern TODO = Pattern.compile("^\\s*(?:[-*•]\\s*)?(?:\\[\\s?\\]|☐|⬜|□)\\s*");
    private static final Pattern BULLET = Pattern.compile("^\\s*(?:[-*•·–—▪◦‣>]+\\s*|\\d{1,3}[.)]\\s+|[a-zA-Z][)]\\s+)");

    public static List<Item> parse(String text) {
        List<Item> out = new ArrayList<Item>();
        if (text == null) return out;
        for (String raw : text.split("\\r?\\n|\\r")) {
            String line = raw.replace('\t', ' ').trim();
            if (line.isEmpty()) continue;
            boolean done = false;
            Matcher m = DONE.matcher(line);
            if (m.find()) { done = true; line = line.substring(m.end()); }
            else {
                m = TODO.matcher(line);
                if (m.find()) line = line.substring(m.end());
                else {
                    m = BULLET.matcher(line);
                    if (m.find()) line = line.substring(m.end());
                }
            }
            line = line.trim();
            if (!line.isEmpty()) out.add(new Item(line, done));
        }
        return out;
    }
}
