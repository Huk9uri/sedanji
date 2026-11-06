package kr.sedanji.correction;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.*;
import java.security.Principal;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class CorrectionController {
    private final CorrectionService service;
    private final ReapprovalFacade writes;
    public CorrectionController(CorrectionService service,ReapprovalFacade writes){this.service=service;this.writes=writes;}
    @PostMapping({"/mentions/{id}/corrections","/restaurants/{id}/revisions","/chefs/{id}/revisions"})
    public ResponseEntity<?> create(@PathVariable long id,@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false)String key,Principal user,HttpServletRequest request) {
        return ResponseEntity.status(201).body(writes.write(kind(request),id,"create",body,key,request.getRequestURI(),user.getName(),request.getSession()));
    }
    @GetMapping({"/mentions/{id}/corrections","/restaurants/{id}/revisions","/chefs/{id}/revisions"})
    public JsonNode list(@PathVariable long id,@RequestParam MultiValueMap<String,String> params,Principal user,HttpServletRequest request) {
        var page=page(params,true,false);String status=params.getFirst("status");
        if(status!=null&&!Set.of("DRAFT","APPLIED").contains(status))throw CorrectionException.invalid();
        return service.list(kind(request),positive(id),user.getName(),status,page[0],page[1]);
    }
    @GetMapping({"/mention-corrections/{id}","/restaurant-revisions/{id}","/chef-revisions/{id}"})
    public JsonNode get(@PathVariable long id,@RequestParam MultiValueMap<String,String> params,Principal user,HttpServletRequest request) {
        var page=page(params,false,true);return service.get(kind(request),positive(id),user.getName(),page[0],page[1]);
    }
    @PatchMapping({"/mention-corrections/{id}","/restaurant-revisions/{id}","/chef-revisions/{id}"})
    public JsonNode patch(@PathVariable long id,@RequestBody JsonNode body,Principal user,HttpServletRequest request) {
        return writes.write(kind(request),id,"patch",body,null,request.getRequestURI(),user.getName(),request.getSession());
    }
    @PostMapping({"/mention-corrections/{id}/rebase","/restaurant-revisions/{id}/rebase","/chef-revisions/{id}/rebase"})
    public JsonNode rebase(@PathVariable long id,@RequestBody JsonNode body,Principal user,HttpServletRequest request) {
        return writes.write(kind(request),id,"rebase",body,null,request.getRequestURI(),user.getName(),request.getSession());
    }
    @PostMapping({"/mention-corrections/{id}/approve","/restaurant-revisions/{id}/approve","/chef-revisions/{id}/approve"})
    public JsonNode approve(@PathVariable long id,@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false)String key,Principal user,HttpServletRequest request) {
        return writes.write(kind(request),id,"approve",body,key,request.getRequestURI(),user.getName(),request.getSession());
    }
    @GetMapping({"/mentions/{id}/history","/restaurants/{id}/history","/chefs/{id}/history"})
    public JsonNode history(@PathVariable long id,@RequestParam MultiValueMap<String,String> params,Principal user,HttpServletRequest request) {
        var page=page(params,false,false);return service.history(kind(request),positive(id),user.getName(),page[0],page[1]);
    }
    private CorrectionKind kind(HttpServletRequest request){
        if(!request.getRequestURI().split("/")[4].matches("[0-9]+"))throw CorrectionException.invalid();
        return CorrectionKind.path(request.getRequestURI());
    }
    private long positive(long id){if(id<1)throw CorrectionException.invalid();return id;}
    private int[] page(MultiValueMap<String,String> params,boolean status,boolean impact){
        String p=impact?"impactPage":"page",s=impact?"impactSize":"size";
        params.forEach((key,values)->{if(values.size()!=1||!(key.equals(p)||key.equals(s)||(status&&key.equals("status"))))throw CorrectionException.invalid();});
        try{
            String rawPage=params.getFirst(p),rawSize=params.getFirst(s);
            if((rawPage!=null&&!rawPage.matches("[0-9]+"))||(rawSize!=null&&!rawSize.matches("[0-9]+")))throw CorrectionException.invalid();
            int page=rawPage==null?0:Integer.parseInt(rawPage),size=rawSize==null?20:Integer.parseInt(rawSize);
            if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw CorrectionException.invalid();return new int[]{page,size};
        }catch(NumberFormatException e){throw CorrectionException.invalid();}
    }
}
