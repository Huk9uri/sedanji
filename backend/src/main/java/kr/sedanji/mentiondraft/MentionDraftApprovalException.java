package kr.sedanji.mentiondraft;

import java.util.Map;

public class MentionDraftApprovalException extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> details;
    public MentionDraftApprovalException(int status, String code, String message) {
        this(status, code, message, Map.of());
    }
    public MentionDraftApprovalException(int status, String code, String message, Map<String, Object> details) {
        super(message); this.status = status; this.code = code; this.details = Map.copyOf(details);
    }
    public int status() { return status; }
    public String code() { return code; }
    public Map<String, Object> details() { return details; }
    public static MentionDraftApprovalException invalid() {
        return new MentionDraftApprovalException(400, "INVALID_INPUT", "입력값과 필수 항목을 확인하세요.");
    }
    public static MentionDraftApprovalException missing() {
        return new MentionDraftApprovalException(404, "RESOURCE_NOT_FOUND", "자료를 찾을 수 없습니다.");
    }
    public static MentionDraftApprovalException duplicate(long id) {
        return new MentionDraftApprovalException(409, "DUPLICATE_RELATION", "같은 셰프·식당·영상의 관계가 이미 있습니다.",
            Map.of("existingMentionId", id));
    }
}
