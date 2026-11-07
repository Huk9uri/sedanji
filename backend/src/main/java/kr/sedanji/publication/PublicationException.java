package kr.sedanji.publication;

public class PublicationException extends RuntimeException {
    private final int status;
    private final String code;
    public PublicationException(int status, String code, String message) {
        super(message); this.status=status; this.code=code;
    }
    public int status() { return status; }
    public String code() { return code; }
    static PublicationException invalid() { return new PublicationException(400,"INVALID_INPUT","입력값과 필수 항목을 확인하세요."); }
    static PublicationException conflict(String code) {
        return new PublicationException(409,code,"현재 관계와 확인 정보가 요청과 다릅니다. 최신 상태를 조회하고 다시 검토하세요.");
    }
}
