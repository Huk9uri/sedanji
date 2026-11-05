package kr.sedanji.mentiondraft;

import com.fasterxml.jackson.databind.JsonNode;

public record MentionDraftApprovalRequest(long expectedVersion) {
    public static MentionDraftApprovalRequest parse(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 1 || !body.has("expectedVersion"))
            throw MentionDraftApprovalException.invalid();
        var version = body.get("expectedVersion");
        if (!version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0)
            throw MentionDraftApprovalException.invalid();
        return new MentionDraftApprovalRequest(version.longValue());
    }
}
