package kr.sedanji.restaurant;

import java.util.Set;
import org.springframework.util.MultiValueMap;

record AdminRestaurantQuery(String query, String publication, boolean needsSourceReview, int page, int size) {
    static AdminRestaurantQuery parse(MultiValueMap<String, String> params, boolean detail) {
        Set<String> allowed = detail ? Set.of("mentionPage", "mentionSize")
            : Set.of("query", "publication", "needsSourceReview", "page", "size");
        params.forEach((key, values) -> {
            if (!allowed.contains(key) || values.size() != 1) throw AdminRestaurantException.invalid();
        });
        String query = params.getFirst("query");
        query = query == null ? "" : query.strip();
        if (query.codePointCount(0, query.length()) > 100) throw AdminRestaurantException.invalid();
        String publication = value(params, "publication", "ALL");
        if (!Set.of("ALL", "PUBLISHED", "NO_PUBLIC_RELATION").contains(publication)) throw AdminRestaurantException.invalid();
        String review = value(params, "needsSourceReview", "false");
        if (!Set.of("true", "false").contains(review)) throw AdminRestaurantException.invalid();
        int page = number(value(params, detail ? "mentionPage" : "page", "0"));
        int size = number(value(params, detail ? "mentionSize" : "size", "20"));
        if (size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw AdminRestaurantException.invalid();
        return new AdminRestaurantQuery(query, publication, review.equals("true"), page, size);
    }
    private static String value(MultiValueMap<String, String> params, String key, String fallback) {
        return params.containsKey(key) ? params.getFirst(key) : fallback;
    }
    private static int number(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) throw AdminRestaurantException.invalid();
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) { throw AdminRestaurantException.invalid(); }
    }
}
