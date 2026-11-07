package pt.lembra.core;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Categorias definidas pelo utilizador (nome + cor + ordem). A categoria 0 ("Geral") existe sempre.
 * A lista vive em memória e é carregada/gravada pela base de dados (Db).
 */
public final class Categories {
    private Categories() {}

    public static final class Cat {
        public int id;
        public String name;
        public int color;
        public int sort;
        public Cat(int id, String name, int color, int sort) { this.id = id; this.name = name; this.color = color; this.sort = sort; }
    }

    public static final int GENERAL = 0;

    /** Paleta de cores disponível ao criar/editar categorias. */
    public static final int[] PALETTE = {0xFF78909C, 0xFF8E6CEF, 0xFF2F7DF6, 0xFF14A3A3, 0xFF2EAD6B, 0xFF8BC34A,
            0xFFF2B705, 0xFFF08A24, 0xFFE5484D, 0xFFE5487B, 0xFFB05CD6, 0xFF8D6E63};

    private static List<Cat> list = defaults();

    public static List<Cat> defaults() {
        List<Cat> d = new ArrayList<Cat>();
        String[] n = {"Geral", "Pessoal", "Trabalho", "Saúde", "Casa", "Compras", "Finanças"};
        int[] c = {0xFF78909C, 0xFF8E6CEF, 0xFF2F7DF6, 0xFF2EAD6B, 0xFFF08A24, 0xFFE5487B, 0xFF14A3A3};
        for (int i = 0; i < n.length; i++) d.add(new Cat(i, n[i], c[i], i));
        return d;
    }

    public static synchronized void set(List<Cat> cats) {
        List<Cat> l = new ArrayList<Cat>(cats);
        boolean hasGeneral = false;
        for (Cat c : l) if (c.id == GENERAL) hasGeneral = true;
        if (!hasGeneral) l.add(0, new Cat(GENERAL, "Geral", PALETTE[0], -1));
        Collections.sort(l, new Comparator<Cat>() {
            public int compare(Cat a, Cat b) { return a.sort != b.sort ? Integer.compare(a.sort, b.sort) : Integer.compare(a.id, b.id); }
        });
        list = l;
    }

    /** Todas as categorias, pela ordem definida. */
    public static synchronized List<Cat> all() { return new ArrayList<Cat>(list); }

    public static synchronized Cat get(int id) {
        for (Cat c : list) if (c.id == id) return c;
        return null;
    }

    public static String name(int id) { Cat c = get(id); return c != null ? c.name : "Geral"; }
    public static int color(int id) { Cat c = get(id); return c != null ? c.color : PALETTE[0]; }

    static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Categoria cujo nome começa por {@code word} (sem acentos nem maiúsculas); -1 se nenhuma. */
    public static int match(String word) {
        String w = norm(word);
        if (w.length() < 2) return -1;
        List<Cat> l = all();
        for (Cat c : l) if (norm(c.name).equals(w)) return c.id;
        for (Cat c : l) if (norm(c.name).startsWith(w)) return c.id;
        return -1;
    }
}
