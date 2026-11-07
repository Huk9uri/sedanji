package kr.sedanji.correction;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CorrectionStore {
    final JdbcTemplate db;
    final ObjectMapper mapper;
    public CorrectionStore(JdbcTemplate db,ObjectMapper mapper) {this.db=db;this.mapper=mapper;}
    public JdbcTemplate db() { return db; }
    public ObjectMapper mapper() { return mapper; }
    ObjectNode object() { return mapper.createObjectNode(); }
    public JsonNode parse(Object value) {
        if(value==null)return mapper.getNodeFactory().nullNode();
        try {return mapper.readTree(value.toString());}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("저장된 스냅샷 오류");}
    }
    public String json(Object value) {
        try{return mapper.writeValueAsString(value);}catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("스냅샷 저장 오류");}
    }
    public String hash(JsonNode value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(sorted(value)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private JsonNode sorted(JsonNode value) {
        if(value.isObject()) {
            ObjectNode out=object();var keys=new TreeSet<String>();value.fieldNames().forEachRemaining(keys::add);
            keys.forEach(k->out.set(k,sorted(value.get(k))));return out;
        }
        if(value.isArray()){var out=mapper.createArrayNode();value.forEach(v->out.add(sorted(v)));return out;}
        return value;
    }
    static final String MENTION_SELECT="""
        select m.id,m.version,m.chef_id as "chefId",m.restaurant_id as "restaurantId",m.video_id as "videoId",
          m.mention_type as "mentionType",m.timestamp_seconds as "timestampSeconds",m.review_note as "verificationNote",
          m.verification_status as "verificationStatus",m.publication_status as "publicationStatus",
          m.restaurant_candidate_id as "candidateId",m.evidence,m.reviewed_by as "reviewedBy",m.reviewed_at as "reviewedAt",
          c.name as "chefName",c.version as "chefVersion",r.name as "restaurantName",r.version as "restaurantVersion",
          v.title as "videoTitle",v.channel_title as "channelTitle",v.published_at as "videoPublishedAt",
          rc.evidence as "candidateEvidence",ij.video_id as "candidateVideoId",original_video.title as "candidateVideoTitle"
        from mentions m join chefs c on c.id=m.chef_id join restaurants r on r.id=m.restaurant_id join videos v on v.id=m.video_id
          left join restaurant_candidates rc on rc.id=m.restaurant_candidate_id left join import_jobs ij on ij.id=rc.import_job_id
          left join videos original_video on original_video.id=ij.video_id
        """;
    public ObjectNode snapshot(CorrectionKind kind,long id) {
        String select=switch(kind) {
            case MENTION -> MENTION_SELECT+" where m.id=?";
            case CHEF -> "select id,version,name from chefs where id=?";
            case RESTAURANT -> """
                select id,version,kakao_place_id as "kakaoPlaceId",name,road_address as "roadAddress",address,
                  longitude,latitude,place_url as "placeUrl" from restaurants where id=?
                """;
        };
        var rows=db.queryForList("select to_jsonb(s)::text from ("+select+") s",String.class,id);
        if(rows.isEmpty())throw CorrectionException.missing();return (ObjectNode)parse(rows.get(0));
    }
    record Revision(long id,long target,long version,long baseVersion,String status,JsonNode base,ObjectNode proposal,
                    JsonNode references,String reason,Map<String,Object> row) {}
    Revision revision(CorrectionKind kind,long id,boolean lock) {
        var rows=db.queryForList("select * from "+kind.table+" where id=?"+kind.predicate()+(lock?" for update":""),id);
        if(rows.isEmpty())throw CorrectionException.missing();var r=rows.get(0);
        JsonNode proposed=parse(r.get("proposed_values"));
        ObjectNode values=proposed.isObject()?(ObjectNode)proposed:object();
        if(kind==CorrectionKind.MENTION&&!proposed.isObject()) {
            for(String field:CorrectionInput.MENTION_FIELDS) {
                String column=switch(field){case "chefId"->"chef_id";case "restaurantId"->"restaurant_id";case "videoId"->"video_id";case "mentionType"->"mention_type";case "timestampSeconds"->"timestamp_seconds";default->"verification_note";};
                values.set(field,mapper.valueToTree(r.get(column)));
            }
        }
        return new Revision(id,((Number)r.get(kind.targetColumn)).longValue(),((Number)r.get("version")).longValue(),
            ((Number)r.get("base_version")).longValue(),r.get("status").toString(),parse(r.get("base_snapshot")),
            values,parse(r.get("reference_snapshot")),(String)r.get("change_reason"),r);
    }
    ObjectNode values(CorrectionKind kind,JsonNode full) {
        var out=object();for(var f:CorrectionInput.editable(kind))out.set(f,full.path(f));return out;
    }
    JsonNode changed(CorrectionKind kind,JsonNode base,JsonNode proposal) {
        var out=mapper.createArrayNode();for(var f:CorrectionInput.editable(kind))if(!same(base.path(f),proposal.path(f)))out.add(f);return out;
    }
    boolean same(JsonNode left,JsonNode right){return left.isNumber()&&right.isNumber()?left.decimalValue().compareTo(right.decimalValue())==0:left.equals(right);}
    public ObjectNode references(CorrectionKind kind,JsonNode values) {
        var refs=object();if(kind!=CorrectionKind.MENTION)return refs;
        refs.set("chef",snapshot(CorrectionKind.CHEF,values.path("chefId").asLong()));
        refs.set("restaurant",snapshot(CorrectionKind.RESTAURANT,values.path("restaurantId").asLong()));
        var rows=db.queryForList("select to_jsonb(v)::text from (select id,title,channel_title,published_at from videos where id=?) v",String.class,values.path("videoId").asText());
        if(rows.isEmpty())throw CorrectionException.missing();refs.set("video",parse(rows.get(0)));return refs;
    }
    List<JsonNode> impact(CorrectionKind kind,long target,JsonNode proposal) {
        String where;Object[] args;
        if(kind==CorrectionKind.CHEF){where=" where m.chef_id=?";args=new Object[]{target};}
        else if(kind==CorrectionKind.RESTAURANT){where=" where m.restaurant_id=?";args=new Object[]{target};}
        else {var current=snapshot(CorrectionKind.MENTION,target);where=" where m.id=? or m.restaurant_id in (?,?) or m.chef_id in (?,?)";args=new Object[]{target,current.path("restaurantId").asLong(),proposal.path("restaurantId").asLong(),current.path("chefId").asLong(),proposal.path("chefId").asLong()};}
        return db.queryForList("select to_jsonb(s)::text from ("+MENTION_SELECT+where+" order by m.id) s",String.class,args).stream().map(this::parse).toList();
    }
    ObjectNode page(List<? extends JsonNode> rows,int page,int size) {
        var out=object();long start=(long)page*size;var items=mapper.createArrayNode();
        rows.stream().skip(start).limit(size).forEach(items::add);out.set("items",items);out.put("page",page);out.put("size",size);
        out.put("totalElements",rows.size());out.put("hasNext",start+size<rows.size());return out;
    }
    ObjectNode detail(CorrectionKind kind,Revision r,int page,int size) {
        var current=snapshot(kind,r.target());var impact=impact(kind,r.target(),r.proposal());
        var confirmation=object();confirmation.set("target",current);confirmation.set("proposal",r.proposal());
        try {confirmation.set("references",references(kind,r.proposal().isEmpty()?values(kind,current):r.proposal()));}
        catch(CorrectionException e) {if(!r.base().isNull())throw e;confirmation.set("references",object().put("unresolved",true));}
        confirmation.set("relations",mapper.valueToTree(impact));
        var out=object();out.put("id",r.id());out.put("targetId",r.target());out.put("kind",kind.name());out.put("version",r.version());
        out.put("baseVersion",r.baseVersion());out.put("status",r.status());out.set("baseSnapshot",r.base());out.set("currentSnapshot",current);
        out.set("proposedValues",r.proposal());out.set("changedFields",changed(kind,r.base(),r.proposal()));out.put("changeReason",r.reason());
        out.set("referenceSnapshot",r.references());out.set("currentReferences",confirmation.path("references"));
        out.put("baseConflict",r.status().equals("DRAFT")&&(r.base().isNull()||r.baseVersion()!=current.path("version").asLong()));
        out.put("referenceConflict",r.status().equals("DRAFT")&&!r.references().equals(confirmation.path("references")));
        out.put("impactHash",hash(confirmation));out.set("impact",page(impact,page,size));
        out.put("publicImpactCount",impact.stream().filter(n->n.path("verificationStatus").asText().equals("APPROVED")&&n.path("publicationStatus").asText().equals("PUBLISHED")).count());
        for(var field:List.of("created_by","updated_by","applied_by","created_at","updated_at","applied_at"))out.set(camel(field),mapper.valueToTree(r.row().get(field)));
        return out;
    }
    private String camel(String name){var b=new StringBuilder();boolean cap=false;for(char c:name.toCharArray()){if(c=='_')cap=true;else{b.append(cap?Character.toUpperCase(c):c);cap=false;}}return b.toString();}
}
