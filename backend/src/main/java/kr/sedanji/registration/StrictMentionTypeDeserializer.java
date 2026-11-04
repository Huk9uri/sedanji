package kr.sedanji.registration;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import kr.sedanji.mention.MentionType;

public class StrictMentionTypeDeserializer extends JsonDeserializer<MentionType> {
    @Override public MentionType deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) throw JsonMappingException.from(parser, "언급 유형을 확인하세요.");
        try { return MentionType.valueOf(parser.getText()); }
        catch (IllegalArgumentException ex) { throw JsonMappingException.from(parser, "언급 유형을 확인하세요."); }
    }
}
