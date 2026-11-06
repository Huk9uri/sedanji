package kr.sedanji;

import java.util.List;
import static org.mockito.Mockito.when;

/** Package-local ExternalApis methods remain inaccessible to production correction code. */
public final class PlaceSelectionFixtures {
    private PlaceSelectionFixtures() {}
    public static void places(ExternalApis external,String query,Models.Place place) {
        when(external.places(query)).thenReturn(new Models.PlaceSearch(List.of(place),1,true));
    }
}
