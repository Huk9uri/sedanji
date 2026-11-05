package kr.sedanji;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.sedanji.mentiondraft.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MentionDraftInputTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @ParameterizedTest @ValueSource(strings={
        "{}", "[]", "null", "{\"restaurantId\":null}", "{\"restaurantId\":0}", "{\"restaurantId\":-1}",
        "{\"restaurantId\":\"1\"}", "{\"restaurantId\":1.0}", "{\"restaurantId\":9223372036854775808}",
        "{\"restaurantId\":1,\"placeSelectionToken\":\"token\"}", "{\"placeSelectionToken\":\" \"}",
        "{\"restaurantId\":1,\"chefId\":\"2\"}", "{\"restaurantId\":1,\"chefId\":false}",
        "{\"restaurantId\":1,\"videoId\":\"\"}", "{\"restaurantId\":1,\"videoId\":2}",
        "{\"restaurantId\":1,\"mentionType\":\"\"}", "{\"restaurantId\":1,\"mentionType\":\"OTHER\"}",
        "{\"restaurantId\":1,\"timestampSeconds\":-1}", "{\"restaurantId\":1,\"timestampSeconds\":1.5}",
        "{\"restaurantId\":1,\"timestampSeconds\":2147483648}", "{\"restaurantId\":1,\"timestampSeconds\":\"0\"}",
        "{\"restaurantId\":1,\"verificationNote\":true}", "{\"restaurantId\":1,\"status\":\"DRAFT\"}",
        "{\"restaurantId\":1,\"unknown\":null}", "{\"restaurantId\":1,\"createdBy\":1}"})
    void rejectsInvalidCreateWithoutCoercion(String json) {
        assertThatThrownBy(() -> MentionDraftInput.create(mapper.readTree(json))).isInstanceOf(MentionDraftException.class);
    }
    @ParameterizedTest @ValueSource(strings={
        "{}", "{\"expectedVersion\":0}", "{\"verificationNote\":null}",
        "{\"expectedVersion\":null,\"chefId\":null}", "{\"expectedVersion\":-1,\"chefId\":null}",
        "{\"expectedVersion\":\"0\",\"chefId\":null}", "{\"expectedVersion\":0.0,\"chefId\":null}",
        "{\"expectedVersion\":0,\"restaurantId\":null}", "{\"expectedVersion\":0,\"placeSelectionToken\":null}",
        "{\"expectedVersion\":0,\"placeSelectionToken\":\"\"}",
        "{\"expectedVersion\":0,\"restaurantId\":1,\"placeSelectionToken\":null}",
        "{\"expectedVersion\":0,\"appliedMentionId\":1}"})
    void rejectsInvalidPatch(String json) {
        assertThatThrownBy(() -> MentionDraftInput.patch(mapper.readTree(json))).isInstanceOf(MentionDraftException.class);
    }
    @Test void distinguishesOmissionNullZeroAndNormalizesNoteAfterLengthCheck() throws Exception {
        var patch = MentionDraftInput.patch(mapper.readTree("{\"expectedVersion\":0,\"chefId\":null,\"timestampSeconds\":0,\"verificationNote\":\"  \"}"));
        assertThat(patch.has("videoId")).isFalse(); assertThat(patch.has("chefId")).isTrue();
        assertThat(patch.values().chefId()).isNull(); assertThat(patch.values().timestampSeconds()).isZero();
        assertThat(patch.values().verificationNote()).isNull(); assertThat(patch.changesPlace()).isFalse();
        var body = mapper.createObjectNode().put("restaurantId", 1).put("verificationNote", " ".repeat(2001));
        assertThatThrownBy(() -> MentionDraftInput.create(body)).isInstanceOf(MentionDraftException.class);
        body.put("verificationNote", "x".repeat(2000));
        assertThat(MentionDraftInput.create(body).values().verificationNote()).hasSize(2000);
        body.put("timestampSeconds", Integer.MAX_VALUE);
        assertThat(MentionDraftInput.create(body).values().timestampSeconds()).isEqualTo(Integer.MAX_VALUE);
        var token = mapper.createObjectNode().put("placeSelectionToken", "x".repeat(101));
        assertThatThrownBy(() -> MentionDraftInput.create(token)).isInstanceOf(MentionDraftException.class);
    }
}
