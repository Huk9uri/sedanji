package kr.sedanji.publication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;

record PublicationInput(long expectedVersion, String reason, String expectedContextHash, String recheckNote) {
    static PublicationInput parse(JsonNode body, boolean restore) {
        if(body==null || !body.isObject()) throw PublicationException.invalid();
        Set<String> fields=restore?Set.of("expectedVersion","reason","expectedContextHash","evidenceRechecked","recheckNote"):
            Set.of("expectedVersion","reason");
        body.fieldNames().forEachRemaining(f->{if(!fields.contains(f))throw PublicationException.invalid();});
        var version=body.get("expectedVersion");
        if(version==null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue()<0)
            throw PublicationException.invalid();
        String reason=text(body.get("reason"),2000);
        String hash=null,note=null;
        if(restore) {
            var checked=body.get("evidenceRechecked");
            if(checked==null || checked.isNull() || (checked.isBoolean() && !checked.booleanValue()))
                throw new PublicationException(400,"RECHECK_REQUIRED","현재 근거를 다시 확인하고 재확인에 체크하세요.");
            if(!checked.isBoolean()) throw PublicationException.invalid();
            hash=text(body.get("expectedContextHash"),64);
            if(!hash.matches("[a-f0-9]{64}"))throw PublicationException.invalid();
            note=text(body.get("recheckNote"),2000);
        }
        return new PublicationInput(version.longValue(),reason,hash,note);
    }
    private static String text(JsonNode value,int max) {
        if(value==null || !value.isTextual())throw PublicationException.invalid();
        String text=value.textValue().strip();
        if(text.isEmpty() || text.length()>max)throw PublicationException.invalid();
        return text;
    }
    ObjectNode normalized(ObjectMapper mapper,boolean restore) {
        var out=mapper.createObjectNode().put("expectedVersion",expectedVersion).put("reason",reason);
        if(restore)out.put("expectedContextHash",expectedContextHash).put("evidenceRechecked",true).put("recheckNote",recheckNote);
        return out;
    }
}
