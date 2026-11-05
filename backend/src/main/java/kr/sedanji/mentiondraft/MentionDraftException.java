package kr.sedanji.mentiondraft;

public class MentionDraftException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long currentVersion;
    public MentionDraftException(int status, String code, String message) {
        this(status, code, message, null);
    }
    public MentionDraftException(int status, String code, String message, Long version) {
        super(message); this.status = status; this.code = code; this.currentVersion = version;
    }
    public int status() { return status; }
    public String code() { return code; }
    public Long currentVersion() { return currentVersion; }
    public static MentionDraftException invalid() {
        return new MentionDraftException(400, "INVALID_INPUT", "입력값과 필수 항목을 확인하세요.");
    }
    public static MentionDraftException missing() {
        return new MentionDraftException(404, "RESOURCE_NOT_FOUND", "자료를 찾을 수 없습니다.");
    }
}
