package kr.sedanji;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static kr.sedanji.Models.*;

@Component
class ExternalApis {
    private final String youtubeKey, kakaoKey;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    ExternalApis(@Value("${sedanji.youtube.key}") String youtubeKey, @Value("${sedanji.kakao.key}") String kakaoKey, ObjectMapper mapper) {
        this.youtubeKey = youtubeKey; this.kakaoKey = kakaoKey; this.mapper = mapper;
    }
    private JsonNode get(String url, String header, String credential) {
        if (credential.isBlank() || credential.equals("KakaoAK ")) throw new ApiFailure("API 키 설정이 필요합니다.");
        try {
            var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header(header, credential).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new ApiFailure("외부 API 요청 실패 (HTTP " + response.statusCode() + ")");
            return mapper.readTree(response.body());
        } catch (ApiFailure ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new ApiFailure("외부 API 요청 중단"); }
        catch (Exception ex) { throw new ApiFailure("외부 API 연결 또는 응답 형식 오류"); }
    }
    Video video(String id) {
        JsonNode data = get("https://www.googleapis.com/youtube/v3/videos?part=snippet&id=" + id, "X-Goog-Api-Key", youtubeKey);
        if (!data.path("items").isArray()) throw new ApiFailure("YouTube 응답 형식 오류");
        if (data.path("items").isEmpty()) throw new IllegalArgumentException("조회 가능한 영상이 없습니다. 비공개·삭제 여부를 확인하세요.");
        var snippet = data.path("items").get(0).path("snippet");
        try {
            return new Video(id, required(snippet, "title"), required(snippet, "channelTitle"),
                OffsetDateTime.parse(required(snippet, "publishedAt")), required(snippet, "description"),
                "설명란에서만 추출했습니다. 주소·상호 누락, 지도 모음 링크, 여러 셰프의 추천은 수동 확인하세요.");
        } catch (RuntimeException ex) { throw new ApiFailure("YouTube 응답 형식 오류"); }
    }
    PlaceSearch places(String query) {
        Map<String, Place> places = new LinkedHashMap<>();
        int total = 0;
        for (int page = 1; page <= 3; page++) {
            JsonNode data = get("https://dapi.kakao.com/v2/local/search/keyword.json?size=15&page=" + page + "&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8), "Authorization", "KakaoAK " + kakaoKey);
            var meta = data.path("meta");
            if (!data.path("documents").isArray() || !meta.path("is_end").isBoolean() || !meta.path("total_count").isIntegralNumber()) throw new ApiFailure("Kakao 응답 형식 오류");
            total = Math.max(total, meta.path("total_count").asInt());
            for (JsonNode doc : data.path("documents")) {
                try {
                    String id = required(doc, "id"), name = required(doc, "place_name");
                    String road = required(doc, "road_address_name"), address = required(doc, "address_name"), url = required(doc, "place_url");
                    double x = Double.parseDouble(required(doc, "x")), y = Double.parseDouble(required(doc, "y"));
                    if (!id.matches("[0-9]+") || name.isBlank() || !url.matches("https?://place\\.map\\.kakao\\.com/" + id) || !Double.isFinite(x) || !Double.isFinite(y) || Math.abs(x) > 180 || Math.abs(y) > 90) throw new IllegalArgumentException();
                    places.put(id, new Place(id, name, road, address, x, y, url));
                } catch (RuntimeException ex) { throw new ApiFailure("Kakao 장소 응답 형식 오류"); }
            }
            if (meta.path("is_end").asBoolean()) return new PlaceSearch(List.copyOf(places.values()), total, places.size() >= total);
        }
        return new PlaceSearch(List.copyOf(places.values()), total, false);
    }
    private String required(JsonNode node, String field) {
        if (!node.path(field).isTextual()) throw new IllegalArgumentException();
        return node.path(field).asText();
    }
    static class ApiFailure extends RuntimeException { ApiFailure(String message) { super(message); } }
}
