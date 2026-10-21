package kr.sedanji.restaurantcandidate;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import kr.sedanji.Models.*;
import org.springframework.stereotype.Component;

/** Conservative address comparison. A match never approves a chef relationship. */
@Component
public class PlaceMatcher {
    private static final Pattern ADDRESS = Pattern.compile("서울\\s+([가-힣]+구)\\s+([가-힣0-9·]+(?:로|길|동|가))\\s*(\\d+(?:-\\d+)?)");
    private String name(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("[^가-힣a-z0-9]", "");
    }
    private String address(String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip().replaceFirst("^서울(?:특별시|시)?\\s*", "서울 ");
        var match = ADDRESS.matcher(normalized);
        return match.matches() ? match.group(1) + "|" + match.group(2) + "|" + match.group(3) : null;
    }
    public String evaluate(String inputName, String inputAddress, PlaceSearch search) {
        if (!search.complete()) return "REVIEW_REQUIRED";
        if (search.candidates().isEmpty()) return "NOT_FOUND";
        String expectedName = name(inputName), expectedAddress = address(inputAddress);
        if (expectedAddress == null || expectedName.isEmpty()) return "REVIEW_REQUIRED";
        int strong = 0, sameAddress = 0;
        for (Place place : search.candidates()) {
            boolean addressMatch = expectedAddress.equals(address(place.roadAddress())) || expectedAddress.equals(address(place.address()));
            if (!addressMatch) continue;
            sameAddress++;
            String actualName = name(place.name());
            boolean nameMatch = actualName.equals(expectedName)
                || (actualName.startsWith(expectedName) && actualName.substring(expectedName.length()).matches("[가-힣a-z0-9]+점"));
            if (nameMatch) strong++;
        }
        return strong == 1 && sameAddress == 1 ? "MATCHED" : "REVIEW_REQUIRED";
    }
}
