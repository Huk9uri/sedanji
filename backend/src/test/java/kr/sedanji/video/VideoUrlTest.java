package kr.sedanji.video;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VideoUrlTest {
    @Test void invalidHostsAndDuplicateIdsAreRejected() {
        for (String url : new String[]{"invalid", "https://youtube.com.evil/watch?v=RMTJPFKRbEk", "https://youtube.com/watch?v=RMTJPFKRbEk&v=RMTJPFKRbEk"})
            assertThatThrownBy(() -> VideoUrl.parse(url)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void officialUrlFormats() {
        assertThat(VideoUrl.parse("https://www.youtube.com/watch?v=RMTJPFKRbEk&t=3")).isEqualTo("RMTJPFKRbEk");
        assertThat(VideoUrl.parse("https://youtu.be/RMTJPFKRbEk")).isEqualTo("RMTJPFKRbEk");
    }
}
