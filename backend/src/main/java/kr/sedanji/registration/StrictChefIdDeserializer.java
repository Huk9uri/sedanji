package kr.sedanji.registration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public class StrictChefIdDeserializer extends JsonDeserializer<Long> {
    @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) throw JsonMappingException.from(parser, "셰프 ID는 정수여야 합니다.");
        return parser.getLongValue();
    }
}
