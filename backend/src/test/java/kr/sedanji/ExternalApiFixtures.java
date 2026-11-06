package kr.sedanji;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Allows integration fixtures in domain packages to stub the package-private adapter API. */
public final class ExternalApiFixtures {
    private ExternalApiFixtures() {}
    public static void places(ExternalApis apis, Models.PlaceSearch result) {
        when(apis.places(anyString())).thenReturn(result);
    }
}
