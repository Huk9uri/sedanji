package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import kr.sedanji.mention.MentionType;

/** Parse only this API strictly, without changing Jackson coercion for existing APIs. */
public final class MentionDraftInput {
    private MentionDraftInput() {}
    private static final Set<String> FIELDS = Set.of("restaurantId", "placeSelectionToken", "chefId", "videoId",
        "mentionType", "timestampSeconds", "verificationNote");
    public record Values(Long restaurantId, String placeSelectionToken, Long chefId, String videoId,
                         MentionType mentionType, Integer timestampSeconds, String verificationNote) {}
    public record Create(Values values) {}
    public record Patch(long expectedVersion, Set<String> fields, Values values) {
        public Patch { fields = Set.copyOf(fields); }
        public boolean has(String field) { return fields.contains(field); }
        public boolean changesPlace() { return has("restaurantId") || has("placeSelectionToken"); }
    }
    public static Create create(JsonNode body) {
        checkFields(body, false);
        return new Create(values(body, true));
    }
    public static Patch patch(JsonNode body) {
        var fields = checkFields(body, true);
        Long version = integer(body.get("expectedVersion"), false);
        if (version == null || fields.size() < 2) throw MentionDraftException.invalid();
        return new Patch(version, fields, values(body, false));
    }
    private static Set<String> checkFields(JsonNode body, boolean patch) {
        if (body == null || !body.isObject()) throw MentionDraftException.invalid();
        var fields = new HashSet<String>();
        body.fieldNames().forEachRemaining(field -> {
            if (!FIELDS.contains(field) && !(patch && field.equals("expectedVersion"))) throw MentionDraftException.invalid();
            fields.add(field);
        });
        return fields;
    }
    private static Values values(JsonNode body, boolean create) {
        boolean id = body.has("restaurantId"), token = body.has("placeSelectionToken");
        if ((create || id || token) && id == token) throw MentionDraftException.invalid();
        Long restaurant = integer(body.get("restaurantId"), true);
        String selection = text(body.get("placeSelectionToken"), 100);
        if ((id && restaurant == null) || (token && (selection == null || selection.isBlank()))) throw MentionDraftException.invalid();
        Long chef = integer(body.get("chefId"), true);
        String video = text(body.get("videoId"), 11);
        if (video != null && video.isBlank()) throw MentionDraftException.invalid();
        String type = text(body.get("mentionType"), 20);
        MentionType mentionType = null;
        if (type != null) {
            try { mentionType = MentionType.valueOf(type); }
            catch (IllegalArgumentException ex) { throw MentionDraftException.invalid(); }
        }
        Long timestamp = integer(body.get("timestampSeconds"), false);
        if (timestamp != null && timestamp > Integer.MAX_VALUE) throw MentionDraftException.invalid();
        String note = text(body.get("verificationNote"), 2000);
        if (note != null) { note = note.trim(); if (note.isEmpty()) note = null; }
        return new Values(restaurant, selection, chef, video, mentionType, timestamp == null ? null : timestamp.intValue(), note);
    }
    private static Long integer(JsonNode node, boolean positive) {
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < (positive ? 1 : 0))
            throw MentionDraftException.invalid();
        return node.longValue();
    }
    private static String text(JsonNode node, int max) {
        if (node == null || node.isNull()) return null;
        if (!node.isTextual() || node.textValue().length() > max) throw MentionDraftException.invalid();
        return node.textValue();
    }
}
