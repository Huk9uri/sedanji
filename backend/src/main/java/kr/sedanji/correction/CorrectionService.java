package kr.sedanji.correction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;
import kr.sedanji.Models.Place;
import kr.sedanji.PlaceSelectionService;
import kr.sedanji.admin.*;
import kr.sedanji.chef.ChefEntity;
import kr.sedanji.restaurant.RestaurantEntity;
import kr.sedanji.mention.*;
import kr.sedanji.video.VideoEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CorrectionService {
    private final CorrectionStore store;
    private final CorrectionCoordinator coordinator;
    private final AdminUserRepository admins;
    private final PlaceSelectionService selections;
    private final EntityManager em;
    public CorrectionService(CorrectionStore store,CorrectionCoordinator coordinator,AdminUserRepository admins,PlaceSelectionService selections,EntityManager em) {
        this.store=store;this.coordinator=coordinator;this.admins=admins;this.selections=selections;this.em=em;
    }
    private AdminUserEntity admin(String username,boolean lock) {
        return (lock?admins.findForUpdate(username):admins.findByUsername(username)).filter(AdminUserEntity::isEnabled)
            .orElseThrow(()->new CorrectionException(403,"ADMIN_INACTIVE","활성 관리자만 사용할 수 있습니다."));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ObjectNode get(CorrectionKind kind,long id,String username,int page,int size) {
        admin(username,false);return store.detail(kind,store.revision(kind,id,false),page,size);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ObjectNode list(CorrectionKind kind,long target,String username,String status,int page,int size) {
        admin(username,false);var targetSnapshot=store.snapshot(kind,target);
        String where=" where "+kind.targetColumn+"=?"+kind.predicate()+(status==null?"":" and status=?");
        Object[] args=status==null?new Object[]{target}:new Object[]{target,status};
        Long total=store.db().queryForObject("select count(*) from "+kind.table+where,Long.class,args);
        var params=new ArrayList<Object>(Arrays.asList(args));params.add(size);params.add((long)page*size);
        var rows=store.db().queryForList("select id,version,status,base_version as \"baseVersion\",change_reason as \"changeReason\",updated_at as \"updatedAt\" from "+kind.table+where+" order by updated_at desc,id desc limit ? offset ?",params.toArray());
        var out=store.object();out.set("target",targetSnapshot);out.set("items",store.mapper().valueToTree(rows));out.put("totalElements",total);out.put("page",page);out.put("size",size);out.put("hasNext",(long)page*size+rows.size()<total);return out;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ObjectNode history(CorrectionKind kind,long target,String username,int page,int size) {
        admin(username,false);store.snapshot(kind,target);
        String column=switch(kind){case MENTION->"mention_id";case RESTAURANT->"restaurant_id";case CHEF->"chef_id";};
        Long total=store.db().queryForObject("select count(*) from admin_audit_events where "+column+"=?",Long.class,target);
        var rows=store.db().queryForList("select id,actor_id as \"actorId\",action,reason,occurred_at as \"occurredAt\",before_value,after_value from admin_audit_events where "+column+"=? order by occurred_at desc,id desc limit ? offset ?",target,size,(long)page*size);
        var items=store.mapper().createArrayNode();for(var row:rows){var n=store.mapper().<ObjectNode>valueToTree(row);n.remove(List.of("before_value","after_value"));n.set("beforeValue",store.parse(row.get("before_value")));n.set("afterValue",store.parse(row.get("after_value")));items.add(n);}
        var out=store.object();out.set("items",items);out.put("totalElements",total);out.put("page",page);out.put("size",size);out.put("hasNext",(long)page*size+rows.size()<total);return out;
    }
    private ObjectNode proposal(CorrectionKind kind,JsonNode initial,JsonNode body,JsonNode current,String username,HttpSession session) {
        ObjectNode values=initial.deepCopy();
        if(kind==CorrectionKind.MENTION) {
            CorrectionInput.mentionChanges(values,body.get("changes"));
            CorrectionInput.mentionChanges(values,values.deepCopy());
        }
        if(kind==CorrectionKind.CHEF && body.has("proposedName"))values.put("name",CorrectionInput.text(body.get("proposedName"),100,true));
        if(kind==CorrectionKind.RESTAURANT && body.has("placeSelectionToken")) {
            var place=selections.resolve(CorrectionInput.text(body.get("placeSelectionToken"),100,true),username,session);
            if(!current.path("kakaoPlaceId").asText().equals(place.id()))throw new CorrectionException(400,"PLACE_ID_MISMATCH","같은 Kakao 장소를 선택하세요.");
            values.put("name",place.name());values.put("roadAddress",place.roadAddress());values.put("address",place.address());
            values.put("longitude",place.longitude());values.put("latitude",place.latitude());values.put("placeUrl",place.placeUrl());
        }
        store.references(kind,values);return values;
    }
    private void eligible(CorrectionKind kind,JsonNode target) {
        if(kind==CorrectionKind.MENTION && !target.path("verificationStatus").asText().equals("APPROVED"))
            throw new CorrectionException(400,"NOT_APPROVED","승인된 관계만 정정할 수 있습니다.");
    }
    private void version(long actual,JsonNode expected,String code) {
        if(actual!=CorrectionInput.number(expected,false))throw CorrectionException.conflict(code);
    }
    private void editable(CorrectionStore.Revision r) {
        if(!r.status().equals("DRAFT"))throw CorrectionException.conflict("REVISION_ALREADY_APPLIED");
    }
    private void targetLock(CorrectionKind kind,long target) {
        if(store.db().queryForList("select id from "+kind.targets+" where id=? for update",target).isEmpty())throw CorrectionException.missing();
    }
    private JsonNode replay(long actor,String key,String path,String hash) {
        var rows=store.db().queryForList("select request_path,request_hash,response_body,expires_at>now() as active from admin_request_receipts where admin_id=? and idempotency_key=?",actor,key);
        if(rows.isEmpty())return null;var row=rows.get(0);
        if(Boolean.TRUE.equals(row.get("active"))) {
            if(!path.equals(row.get("request_path")) || !hash.equals(row.get("request_hash")))throw CorrectionException.conflict("IDEMPOTENCY_CONFLICT");
            return store.parse(row.get("response_body"));
        }
        store.db().update("delete from admin_request_receipts where admin_id=? and idempotency_key=?",actor,key);return null;
    }
    private void receipt(long actor,String key,String path,String hash,int status,JsonNode result) {
        store.db().update("insert into admin_request_receipts(admin_id,idempotency_key,request_path,request_hash,response_status,response_body,expires_at) values (?,?,?,?,?,cast(? as jsonb),now()+interval '24 hours')",actor,key,path,hash,status,store.json(result));
    }
    private void audit(CorrectionKind kind,CorrectionStore.Revision revision,AdminUserEntity actor,String action,JsonNode before,JsonNode after) {
        audit(kind,revision,actor,action,before,after,null);
    }
    /** Scalars captured before mutation; no mutable query tree or post-apply recomputation. */
    private record ApprovalConfirmation(int schemaVersion,String expectedImpactHash,String validatedImpactHash,
        long targetVersion,long revisionVersion,long impactTotalElements,long publicImpactCount) {}
    private void audit(CorrectionKind kind,CorrectionStore.Revision revision,AdminUserEntity actor,String action,
                       JsonNode before,JsonNode after,ApprovalConfirmation confirmation) {
        var envelope=store.object();envelope.put("actorId",actor.getId());envelope.put("actorUsername",actor.getUsername());
        if(confirmation!=null)envelope.set("approvalConfirmation",store.mapper().valueToTree(confirmation));
        envelope.set("revision",store.detail(kind,revision,0,20));envelope.set("target",after);
        Long mention=kind==CorrectionKind.MENTION?revision.target():null;
        Long restaurant=kind==CorrectionKind.RESTAURANT?Long.valueOf(revision.target()):kind==CorrectionKind.MENTION?Long.valueOf(after.path("restaurantId").asLong()):null;
        Long chef=kind==CorrectionKind.CHEF?Long.valueOf(revision.target()):kind==CorrectionKind.MENTION?Long.valueOf(after.path("chefId").asLong()):null;
        store.db().update("""
            insert into admin_audit_events(actor_id,mention_id,draft_id,restaurant_id,chef_id,restaurant_revision_id,chef_revision_id,action,before_value,after_value,reason)
            values (?,?,?,?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?)
            """,actor.getId(),mention,kind==CorrectionKind.MENTION?revision.id():null,restaurant,chef,
            kind==CorrectionKind.RESTAURANT?revision.id():null,kind==CorrectionKind.CHEF?revision.id():null,
            action,store.json(before),store.json(envelope),revision.reason());
    }
    @Transactional
    public JsonNode create(CorrectionKind kind,long target,JsonNode body,String key,String path,String username,HttpSession session) {
        CorrectionInput.validate(kind,"create",body);var actor=admin(username,true);String hash=store.hash(body);
        var replay=replay(actor.getId(),key,path,hash);if(replay!=null)return replay;
        coordinator.lock();targetLock(kind,target);var current=store.snapshot(kind,target);eligible(kind,current);
        version(current.path("version").asLong(),body.get("expectedVersion"),"STALE_BASE_VERSION");
        var open=store.db().queryForList("select id from "+kind.table+" where "+kind.targetColumn+"=? and status='DRAFT'"+kind.predicate(),target);
        if(!open.isEmpty())throw CorrectionException.conflict("OPEN_REVISION_EXISTS");
        var values=proposal(kind,store.values(kind,current),body,current,username,session);var refs=store.references(kind,values);
        String reason=CorrectionInput.text(body.get("changeReason"),2000,true);
        String columns=kind.targetColumn+",base_version,base_snapshot,proposed_values,changed_fields,reference_snapshot,change_reason,created_by,updated_by";
        var args=new ArrayList<Object>(Arrays.asList(target,current.path("version").asLong(),store.json(current),store.json(values),store.json(store.changed(kind,current,values)),store.json(refs),reason,actor.getId(),actor.getId()));
        String placeholders="?,?,cast(? as jsonb),cast(? as jsonb),cast(? as jsonb),cast(? as jsonb),?,?,?";
        if(kind==CorrectionKind.MENTION) {
            columns+=",kind,restaurant_id,chef_id,video_id,mention_type,timestamp_seconds,verification_note";
            placeholders+=",'CORRECTION',?,?,?,?,?,?";
            args.add(values.path("restaurantId").asLong());args.add(values.path("chefId").asLong());args.add(values.path("videoId").asText());args.add(values.path("mentionType").asText());
            args.add(values.path("timestampSeconds").isNull()?null:values.path("timestampSeconds").asInt());args.add(values.path("verificationNote").isNull()?null:values.path("verificationNote").asText());
        }
        long id=store.db().queryForObject("insert into "+kind.table+"("+columns+") values ("+placeholders+") returning id",Long.class,args.toArray());
        var revision=store.revision(kind,id,false);audit(kind,revision,actor,"CORRECTION_CREATED",store.mapper().getNodeFactory().nullNode(),current);
        var result=store.detail(kind,revision,0,20);receipt(actor.getId(),key,path,hash,201,result);return result;
    }
    @Transactional
    public JsonNode patch(CorrectionKind kind,long id,JsonNode body,String username,HttpSession session,boolean rebase) {
        CorrectionInput.validate(kind,rebase?"rebase":"patch",body);var actor=admin(username,true);coordinator.lock();
        var revision=store.revision(kind,id,true);editable(revision);version(revision.version(),body.get("expectedVersion"),"STALE_VERSION");
        targetLock(kind,revision.target());var current=store.snapshot(kind,revision.target());eligible(kind,current);
        var before=store.detail(kind,revision,0,20);
        JsonNode base=revision.base();ObjectNode values=revision.proposal().deepCopy();long baseVersion=revision.baseVersion();
        JsonNode refs=revision.references();
        if(rebase) {
            version(current.path("version").asLong(),body.get("expectedBaseVersion"),"STALE_BASE_VERSION");
            var next=store.values(kind,current);
            if(kind==CorrectionKind.RESTAURANT) {
                // The official place is one complete stored snapshot, including fields
                // that happened to equal the previous baseline and nullable addresses.
                next=values.deepCopy();
            } else if(base.isNull()) {
                // Legacy proposals are explicit stored values, never guessed baseline data.
                next=values.deepCopy();
                CorrectionInput.mentionChanges(next,next.deepCopy());
            } else for(var f:CorrectionInput.editable(kind))if(!store.same(base.path(f),values.path(f)))next.set(f,values.path(f));
            values=next;base=current;baseVersion=current.path("version").asLong();refs=store.references(kind,values);
        } else {
            if(!base.isNull() && kind==CorrectionKind.MENTION && !refs.equals(store.references(kind,values)))throw CorrectionException.conflict("STALE_BASE_VERSION");
            values=proposal(kind,values,body,current,username,session);
            // Only explicit rebase accepts new reference versions. A PATCH cannot hide a stale baseline.
            if(kind==CorrectionKind.MENTION && body.has("changes")) {
                var changes=body.path("changes");
                if(changes.has("chefId")||changes.has("restaurantId")||changes.has("videoId"))refs=store.references(kind,values);
            }
        }
        String reason=body.has("changeReason")?CorrectionInput.text(body.get("changeReason"),2000,true):revision.reason();
        store.db().update("update "+kind.table+" set base_version=?,base_snapshot=cast(? as jsonb),proposed_values=cast(? as jsonb),changed_fields=cast(? as jsonb),reference_snapshot=cast(? as jsonb),change_reason=?,version=version+1,updated_by=?,updated_at=clock_timestamp() where id=?",
            baseVersion,store.json(base),store.json(values),store.json(store.changed(kind,base,values)),store.json(refs),reason,actor.getId(),id);
        syncMention(kind,id,values);
        var after=store.revision(kind,id,false);audit(kind,after,actor,rebase?"CORRECTION_REBASED":"CORRECTION_UPDATED",before,current);
        return store.detail(kind,after,0,20);
    }
    private void syncMention(CorrectionKind kind,long id,JsonNode values) {
        if(kind!=CorrectionKind.MENTION)return;
        store.db().update("update mention_drafts set restaurant_id=?,chef_id=?,video_id=?,mention_type=?,timestamp_seconds=?,verification_note=? where id=?",values.path("restaurantId").asLong(),values.path("chefId").asLong(),values.path("videoId").asText(),values.path("mentionType").asText(),values.path("timestampSeconds").isNull()?null:values.path("timestampSeconds").asInt(),values.path("verificationNote").isNull()?null:values.path("verificationNote").asText(),id);
    }
    @Transactional
    public JsonNode approve(CorrectionKind kind,long id,JsonNode body,String key,String path,String username) {
        CorrectionInput.validate(kind,"approve",body);var actor=admin(username,true);String hash=store.hash(body);
        var replay=replay(actor.getId(),key,path,hash);if(replay!=null)return replay;
        coordinator.lock();var revision=store.revision(kind,id,true);editable(revision);
        version(revision.version(),body.get("expectedVersion"),"STALE_VERSION");targetLock(kind,revision.target());
        var current=store.snapshot(kind,revision.target());eligible(kind,current);
        if(revision.base().isNull()||revision.baseVersion()!=current.path("version").asLong())throw CorrectionException.conflict("STALE_BASE_VERSION");
        if(!revision.references().equals(store.references(kind,revision.proposal())))throw CorrectionException.conflict("STALE_BASE_VERSION");
        var detail=store.detail(kind,revision,0,20);
        if(!detail.path("impactHash").asText().equals(body.path("expectedImpactHash").asText()))throw CorrectionException.conflict("IMPACT_CHANGED");
        var confirmation=new ApprovalConfirmation(1,body.path("expectedImpactHash").asText(),detail.path("impactHash").asText(),
            current.path("version").asLong(),revision.version(),detail.path("impact").path("totalElements").asLong(),
            detail.path("publicImpactCount").asLong());
        if(store.changed(kind,current,revision.proposal()).isEmpty())throw new CorrectionException(400,"NO_CHANGES","적용할 변경이 없습니다.");
        var p=revision.proposal();
        switch(kind) {
            case CHEF -> em.find(ChefEntity.class,revision.target()).correctName(p.path("name").asText());
            case RESTAURANT -> em.find(RestaurantEntity.class,revision.target()).correctPlace(new Place(current.path("kakaoPlaceId").asText(),p.path("name").asText(),nullable(p,"roadAddress"),nullable(p,"address"),p.path("longitude").asDouble(),p.path("latitude").asDouble(),p.path("placeUrl").asText()));
            case MENTION -> {
                var duplicate=store.db().queryForList("select id from mentions where chef_id=? and restaurant_id=? and video_id=? and id<>?",p.path("chefId").asLong(),p.path("restaurantId").asLong(),p.path("videoId").asText(),revision.target());
                if(!duplicate.isEmpty())throw CorrectionException.conflict("DUPLICATE_RELATION");
                em.find(MentionEntity.class,revision.target()).correct(em.find(ChefEntity.class,p.path("chefId").asLong()),em.find(RestaurantEntity.class,p.path("restaurantId").asLong()),em.find(VideoEntity.class,p.path("videoId").asText()),MentionType.valueOf(p.path("mentionType").asText()),p.path("timestampSeconds").isNull()?null:p.path("timestampSeconds").asInt(),nullable(p,"verificationNote"),actor);
            }
        }
        em.flush();
        String extra=kind==CorrectionKind.MENTION?",applied_mention_id="+revision.target():"";
        store.db().update("update "+kind.table+" set status='APPLIED',version=version+1,applied_by=?,applied_at=clock_timestamp(),updated_by=?,updated_at=clock_timestamp()"+extra+" where id=?",actor.getId(),actor.getId(),id);
        var after=store.snapshot(kind,revision.target());var applied=store.revision(kind,id,false);audit(kind,applied,actor,"CORRECTION_APPROVED",current,after,confirmation);
        var result=store.detail(kind,applied,0,20);result.put("targetVersion",after.path("version").asLong());
        if(kind==CorrectionKind.MENTION){result.put("restaurantId",after.path("restaurantId").asLong());result.put("previousRestaurantId",current.path("restaurantId").asLong());result.put("mentionId",revision.target());}
        receipt(actor.getId(),key,path,hash,200,result);return result;
    }
    private String nullable(JsonNode node,String field){return node.path(field).isNull()?null:node.path(field).asText();}
}
