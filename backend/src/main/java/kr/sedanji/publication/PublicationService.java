package kr.sedanji.publication;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import kr.sedanji.admin.*;
import kr.sedanji.correction.*;
import kr.sedanji.mention.MentionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationService {
    private final CorrectionStore store;
    private final CorrectionCoordinator coordinator;
    private final AdminUserRepository admins;
    private final MentionRepository mentions;
    private final PublicationQueryService query;
    public PublicationService(CorrectionStore store,CorrectionCoordinator coordinator,AdminUserRepository admins,
                              MentionRepository mentions,PublicationQueryService query) {
        this.store=store;this.coordinator=coordinator;this.admins=admins;this.mentions=mentions;this.query=query;
    }
    @Transactional
    public JsonNode write(long id,boolean restore,JsonNode body,String key,String username) {
        var input=PublicationInput.parse(body,restore);
        var actor=admins.findForUpdate(username).filter(AdminUserEntity::isEnabled)
            .orElseThrow(()->new PublicationException(403,"ADMIN_INACTIVE","활성 관리자만 사용할 수 있습니다."));
        String path="/api/admin/mentions/"+id+(restore?"/restore":"/suspend");
        var identity=store.mapper().createObjectNode().put("method","POST").put("path",path);
        identity.set("body",input.normalized(store.mapper(),restore));String hash=store.hash(identity);
        var receipts=store.db().queryForList("select request_path,request_hash,response_body,expires_at>now() as active from admin_request_receipts where admin_id=? and idempotency_key=?",actor.getId(),key);
        if(!receipts.isEmpty()) {
            var receipt=receipts.get(0);
            if(Boolean.TRUE.equals(receipt.get("active"))) {
                if(!path.equals(receipt.get("request_path")) || !hash.equals(receipt.get("request_hash")))
                    throw PublicationException.conflict("IDEMPOTENCY_CONFLICT");
                return store.parse(receipt.get("response_body"));
            }
            store.db().update("delete from admin_request_receipts where admin_id=? and idempotency_key=?",actor.getId(),key);
        }
        coordinator.lock();
        var mention=mentions.findForUpdate(id).orElseThrow(()->new PublicationException(404,"RESOURCE_NOT_FOUND","관계를 찾을 수 없습니다."));
        if(mention.getVersion()!=input.expectedVersion())throw PublicationException.conflict("STALE_VERSION");
        var context=query.context(id);var before=context.path("mention");
        if(!before.path("verificationStatus").asText().equals("APPROVED"))throw PublicationException.conflict("INVALID_STATE");
        String publication=before.path("publicationStatus").asText();
        if(publication.equals(restore?"PUBLISHED":"SUSPENDED"))throw PublicationException.conflict(restore?"ALREADY_PUBLISHED":"ALREADY_SUSPENDED");
        if(!publication.equals(restore?"SUSPENDED":"PUBLISHED"))throw PublicationException.conflict("INVALID_STATE");
        String validatedHash=store.hash(context);
        if(restore && !validatedHash.equals(input.expectedContextHash()))throw PublicationException.conflict("CONTEXT_CHANGED");
        if(restore)mention.restore(actor);else mention.suspend();
        mentions.flush();
        var after=store.snapshot(CorrectionKind.MENTION,id);
        var envelope=store.mapper().createObjectNode().put("actorId",actor.getId()).put("actorUsername",actor.getUsername());
        envelope.set("target",after);envelope.set("references",context.path("references"));
        if(restore)envelope.set("publicationConfirmation",store.mapper().createObjectNode()
            .put("schemaVersion",1).put("evidenceRechecked",true).put("recheckNote",input.recheckNote()).put("validatedContextHash",validatedHash));
        String action=restore?"RESTORE_MENTION":"SUSPEND_MENTION";
        record Event(long id,OffsetDateTime occurredAt) {}
        var event=store.db().queryForObject("""
            insert into admin_audit_events(actor_id,mention_id,restaurant_id,chef_id,action,before_value,after_value,reason,occurred_at)
            values (?,?,?,?,?,cast(? as jsonb),cast(? as jsonb),?,date_trunc('milliseconds',clock_timestamp())) returning id,occurred_at
            """,(rs,row)->new Event(rs.getLong("id"),rs.getObject("occurred_at",OffsetDateTime.class)),actor.getId(),id,after.path("restaurantId").asLong(),after.path("chefId").asLong(),action,
            store.json(before),store.json(envelope),input.reason());
        var result=store.mapper().valueToTree(new PublicationResult(action,id,after.path("restaurantId").asLong(),input.expectedVersion(),
            mention.getVersion(),"APPROVED",after.path("publicationStatus").asText(),event.id(),event.occurredAt()));
        store.db().update("insert into admin_request_receipts(admin_id,idempotency_key,request_path,request_hash,response_status,response_body,expires_at) values (?,?,?,?,200,cast(? as jsonb),now()+interval '24 hours')",
            actor.getId(),key,path,hash,store.json(result));
        return result;
    }
}
