package kr.sedanji;
import java.util.List;
import kr.sedanji.restaurantcandidate.PlaceMatcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static kr.sedanji.Models.*;
class PlaceMatcherTest {
    private final PlaceMatcher matcher = new PlaceMatcher();
    private Place place(String id, String name, String road) { return new Place(id,name,road,"",127,37,"http://place.map.kakao.com/"+id); }
    @Test void normalizationAndAddressDistinguishBranches() {
        var result = new PlaceSearch(List.of(place("1","뽀요 베리 망원점","서울특별시 마포구 월드컵로13길 55-15"),place("2","뽀요베리","서울 강남구 테헤란로 1")),2,true);
        assertThat(matcher.evaluate("뽀요베리","서울 마포구 월드컵로13길 55-15",result)).isEqualTo("MATCHED");
    }
    @Test void nameOnlyMissingRoadAndDifferentSubnumbersRequireReview() {
        for (String road : List.of("", "서울 마포구 월드컵로13길 55", "서울 마포구 월드컵로13길 55-16"))
            assertThat(matcher.evaluate("뽀요베리","서울 마포구 월드컵로13길 55-15",new PlaceSearch(List.of(place("1","뽀요베리",road)),1,true))).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void missingInputAddressAndMultipleStrongCandidatesDoNotMatch() {
        var result = new PlaceSearch(List.of(place("1","뽀요베리","서울 마포구 월드컵로13길 55-15"),place("2","뽀요베리","서울 마포구 월드컵로13길 55-15")),2,true);
        assertThat(matcher.evaluate("뽀요베리",null,result)).isEqualTo("REVIEW_REQUIRED");
        assertThat(matcher.evaluate("뽀요베리","서울 마포구 월드컵로13길 55-15",result)).isEqualTo("REVIEW_REQUIRED");
    }
    @Test void emptyAndIncompleteAreDistinct() {
        assertThat(matcher.evaluate("상호",null,new PlaceSearch(List.of(),0,true))).isEqualTo("NOT_FOUND");
        assertThat(matcher.evaluate("상호",null,new PlaceSearch(List.of(),10,false))).isEqualTo("REVIEW_REQUIRED");
    }
}
