package kr.sedanji;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescriptionExtractorTest {
    private final DescriptionExtractor extractor = new DescriptionExtractor();
    @Test void multiRestaurantEvidenceIsKeptSeparate() {
        var result = extractor.extract("🥘 뽀요베리\n📍서울 마포구 월드컵로13길 55-15\n메뉴\n\n🍣 모리본\n📍서울 광진구 긴고랑로15길 6");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("뽀요베리");
        assertThat(result.get(1).address()).contains("광진구");
        assertThat(result.get(1).sourceLine()).isEqualTo(5);
    }
    @Test void explicitNameWithoutAddressRemainsCandidate() {
        var result = extractor.extract("중식당 [계향각]을 다녀왔습니다.");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).address()).isEmpty();
    }
    @Test void mapCollectionsAndChannelLinksAreNotRestaurants() {
        assertThat(extractor.extract("📍셰프 맛지도\nhttps://naver.me/abc\n🍚 이원일식탁 ➡️ https://app.catchtable.co.kr/ct/shop/211table")).isEmpty();
    }
    @Test void invalidHostsAndDuplicateIdsAreRejected() {
        for (String url : new String[]{"invalid", "https://youtube.com.evil/watch?v=RMTJPFKRbEk", "https://youtube.com/watch?v=RMTJPFKRbEk&v=RMTJPFKRbEk"})
            assertThatThrownBy(() -> DescriptionExtractor.videoId(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void officialUrlFormats() {
        assertThat(DescriptionExtractor.videoId("https://www.youtube.com/watch?v=RMTJPFKRbEk&t=3")).isEqualTo("RMTJPFKRbEk");
        assertThat(DescriptionExtractor.videoId("https://youtu.be/RMTJPFKRbEk")).isEqualTo("RMTJPFKRbEk");
    }
    @Test void fiveRealDescriptionsExposeLimits() throws Exception {
        try (var stream = getClass().getResourceAsStream("/video-cases.json")) {
            var cases = new ObjectMapper().readTree(stream);
            assertThat(cases.size()).isEqualTo(5);
            for (var video : cases) {
                var result = extractor.extract(video.get("description").asText());
                if (video.get("videoId").asText().equals("g5_wJy6pOHg")) {
                    assertThat(result).hasSize(1);
                    assertThat(result.get(0).name()).isEqualTo("계향각");
                    assertThat(result.get(0).address()).isEmpty();
                } else assertThat(result).isEmpty();
            }
        }
    }
}
