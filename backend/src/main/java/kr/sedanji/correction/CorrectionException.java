package kr.sedanji.correction;

public class CorrectionException extends RuntimeException {
    private final int status;
    private final String code;
    public CorrectionException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static CorrectionException invalid() { return new CorrectionException(400,"INVALID_INPUT","입력값을 확인하세요."); }
    public static CorrectionException missing() { return new CorrectionException(404,"NOT_FOUND","대상을 찾을 수 없습니다."); }
    public static CorrectionException conflict(String code) { return new CorrectionException(409,code,"최신 정보와 제안을 비교한 뒤 다시 검토하세요."); }
}
