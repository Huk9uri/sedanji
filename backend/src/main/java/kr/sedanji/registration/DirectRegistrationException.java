package kr.sedanji.registration;

public class DirectRegistrationException extends RuntimeException {
    private final int status;
    private final String code;
    private final Long existingMentionId;
    public DirectRegistrationException(int status, String code, String message) {
        this(status, code, message, null);
    }
    public DirectRegistrationException(int status, String code, String message, Long existingMentionId) {
        super(message); this.status = status; this.code = code; this.existingMentionId = existingMentionId;
    }
    public int status() { return status; }
    public String code() { return code; }
    public Long existingMentionId() { return existingMentionId; }
    public static DirectRegistrationException invalid() {
        return new DirectRegistrationException(400, "INVALID_INPUT", "입력값과 필수 항목을 확인하세요.");
    }
    public static DirectRegistrationException duplicate(long id) {
        return new DirectRegistrationException(409, "DUPLICATE_RELATION", "같은 셰프·식당·영상의 관계가 이미 있습니다.", id);
    }
}
