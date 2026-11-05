package kr.sedanji;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.sedanji.mentiondraft.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MentionDraftApprovalInputTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void onlyNonnegativeLongIntegerAndNoExtraFieldsAreAccepted() throws Exception {
        for (String body : new String[]{"null", "[]", "{}", "{\"expectedVersion\":null}",
            "{\"expectedVersion\":\"0\"}", "{\"expectedVersion\":0.0}", "{\"expectedVersion\":-1}",
            "{\"expectedVersion\":9223372036854775808}", "{\"expectedVersion\":0,\"chefId\":1}"}) {
            assertThatThrownBy(() -> MentionDraftApprovalRequest.parse(mapper.readTree(body)))
                .isInstanceOf(MentionDraftApprovalException.class);
        }
        assertThat(MentionDraftApprovalRequest.parse(mapper.readTree("{\"expectedVersion\":0}")).expectedVersion()).isZero();
        assertThat(MentionDraftApprovalRequest.parse(mapper.readTree("{\"expectedVersion\":9223372036854775807}")).expectedVersion())
            .isEqualTo(Long.MAX_VALUE);
    }
}
