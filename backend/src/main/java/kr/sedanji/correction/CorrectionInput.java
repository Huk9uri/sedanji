package kr.sedanji.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

final class CorrectionInput {
    static final List<String> MENTION_FIELDS=List.of("chefId","restaurantId","videoId","mentionType","timestampSeconds","verificationNote");
    static final List<String> PLACE_FIELDS=List.of("name","roadAddress","address","longitude","latitude","placeUrl");
    static void fields(JsonNode body, String... allowed) {
        if (body==null || !body.isObject()) throw CorrectionException.invalid();
        var names=Set.of(allowed); body.fieldNames().forEachRemaining(f->{if(!names.contains(f)) throw CorrectionException.invalid();});
    }
    static long number(JsonNode node, boolean positive) {
        if(node==null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue()<(positive?1:0)) throw CorrectionException.invalid();
        return node.longValue();
    }
    static String text(JsonNode node,int max,boolean required) {
        if(node==null || node.isNull()) { if(required)throw CorrectionException.invalid(); return null; }
        if(!node.isTextual())throw CorrectionException.invalid();
        String s=node.textValue().strip(); if(s.length()>max || (required && s.isEmpty()))throw CorrectionException.invalid(); return s.isEmpty()?null:s;
    }
    static void validate(CorrectionKind kind,String action,JsonNode body) {
        if(action.equals("approve")) {
            fields(body,"expectedVersion","expectedImpactHash");
            String hash=text(body.get("expectedImpactHash"),64,true);
            if(!hash.matches("[a-f0-9]{64}"))throw CorrectionException.invalid();
        } else if(action.equals("rebase")) {
            fields(body,"expectedVersion","expectedBaseVersion"); number(body.get("expectedBaseVersion"),false);
        } else {
            if(kind==CorrectionKind.MENTION)fields(body,"expectedVersion","changes","changeReason");
            else if(kind==CorrectionKind.CHEF)fields(body,"expectedVersion","proposedName","changeReason");
            else fields(body,"expectedVersion","placeSelectionToken","changeReason");
            if(action.equals("create") || body.has("changeReason"))text(body.get("changeReason"),2000,true);
            if(kind==CorrectionKind.MENTION && (action.equals("create") || body.has("changes"))) {
                fields(body.get("changes"),MENTION_FIELDS.toArray(String[]::new));
            }
            if(kind==CorrectionKind.CHEF && (action.equals("create") || body.has("proposedName")))text(body.get("proposedName"),100,true);
            if(kind==CorrectionKind.RESTAURANT && (action.equals("create") || body.has("placeSelectionToken")))text(body.get("placeSelectionToken"),100,true);
            if(action.equals("patch") && body.size()<2)throw CorrectionException.invalid();
        }
        number(body.get("expectedVersion"),false);
    }
    static void mentionChanges(ObjectNode values,JsonNode changes) {
        if(changes==null)return;
        changes.fields().forEachRemaining(e->{
            String f=e.getKey();JsonNode v=e.getValue();
            switch(f) {
                case "chefId","restaurantId" -> values.put(f,number(v,true));
                case "videoId" -> {String s=text(v,11,true);if(!s.matches("[A-Za-z0-9_-]{11}"))throw CorrectionException.invalid();values.put(f,s);}
                case "mentionType" -> {String s=text(v,20,true);if(!Set.of("REGULAR","RECOMMENDED","VISITED","MENTIONED").contains(s))throw CorrectionException.invalid();values.put(f,s);}
                case "timestampSeconds" -> {if(v.isNull())values.putNull(f);else {long n=number(v,false);if(n>Integer.MAX_VALUE)throw CorrectionException.invalid();values.put(f,n);}}
                case "verificationNote" -> values.put(f,text(v,2000,false));
                default -> throw CorrectionException.invalid();
            }
        });
    }
    static List<String> editable(CorrectionKind kind) { return kind==CorrectionKind.MENTION?MENTION_FIELDS:kind==CorrectionKind.CHEF?List.of("name"):PLACE_FIELDS; }
}
