package kr.sedanji.restaurant;

class AdminRestaurantException extends RuntimeException {
    final int status;
    final String code;
    AdminRestaurantException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    static AdminRestaurantException invalid() {
        return new AdminRestaurantException(400, "INVALID_QUERY", "조회 조건을 확인하세요.");
    }
}
