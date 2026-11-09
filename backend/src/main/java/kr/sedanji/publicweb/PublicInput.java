package kr.sedanji.publicweb;

import java.math.BigDecimal;
import java.util.Set;
import org.springframework.util.MultiValueMap;

final class PublicInput {
    record Bounds(double west, double south, double east, double north, Long chefId) {}
    record Page(String query, int page, int size) {}
    static void allowed(MultiValueMap<String, String> p, String... names) {
        var allowed = Set.of(names);
        p.forEach((k, v) -> { if (!allowed.contains(k) || v.size() != 1) throw invalid(); });
    }
    static Bounds bounds(MultiValueMap<String, String> p) {
        allowed(p, "west", "south", "east", "north", "chefId");
        var w = decimal(p.getFirst("west")); var s = decimal(p.getFirst("south"));
        var e = decimal(p.getFirst("east")); var n = decimal(p.getFirst("north"));
        if (w.compareTo(BigDecimal.valueOf(-180)) < 0 || e.compareTo(BigDecimal.valueOf(180)) > 0
            || s.compareTo(BigDecimal.valueOf(-90)) < 0 || n.compareTo(BigDecimal.valueOf(90)) > 0
            || w.compareTo(e) >= 0 || s.compareTo(n) >= 0
            || w.doubleValue() >= e.doubleValue() || s.doubleValue() >= n.doubleValue()) throw invalid();
        Long chef = p.containsKey("chefId") ? positive(p.getFirst("chefId")) : null;
        if (e.subtract(w).compareTo(new BigDecimal("0.25")) > 0
            || n.subtract(s).compareTo(new BigDecimal("0.2")) > 0)
            throw new Failure(422, "VIEWPORT_TOO_WIDE", "지도를 확대해서 다시 검색하세요.");
        return new Bounds(w.doubleValue(), s.doubleValue(), e.doubleValue(), n.doubleValue(), chef);
    }
    static Page page(MultiValueMap<String, String> p, boolean source) {
        allowed(p, source ? new String[]{"sourcePage", "sourceSize"} : new String[]{"query", "page", "size"});
        String query = p.getFirst("query"); query = query == null ? "" : query.strip();
        if (query.codePointCount(0, query.length()) > 100) throw invalid();
        int page = integer(p, source ? "sourcePage" : "page", 0);
        int size = integer(p, source ? "sourceSize" : "size", source ? 20 : 50);
        if (size < 1 || size > 100) throw invalid();
        return new Page(query, page, size);
    }
    static long positive(String value) {
        try { if (value == null || !value.matches("[0-9]+")) throw invalid();
            long id = Long.parseLong(value); if (id <= 0) throw invalid(); return id;
        } catch (NumberFormatException ex) { throw invalid(); }
    }
    private static int integer(MultiValueMap<String, String> p, String key, int fallback) {
        if (!p.containsKey(key)) return fallback;
        try { String v = p.getFirst(key); if (v == null || !v.matches("[0-9]+")) throw invalid(); return Integer.parseInt(v); }
        catch (NumberFormatException ex) { throw invalid(); }
    }
    private static BigDecimal decimal(String v) {
        try {
            if (v == null || v.length() > 100 || !v.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) throw invalid();
            double number=Double.parseDouble(v);
            if (!Double.isFinite(number)) throw invalid();
            var decimal=new BigDecimal(v);
            // Avoid enormous BigDecimal alignment for values below double precision.
            return decimal.scale()>400 ? BigDecimal.valueOf(number) : decimal;
        } catch (NumberFormatException ex) { throw invalid(); }
    }
    static Failure invalid() { return new Failure(400, "INVALID_QUERY", "조회 조건을 확인하세요."); }
    static final class Failure extends RuntimeException {
        final int status; final String code;
        Failure(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    }
}
