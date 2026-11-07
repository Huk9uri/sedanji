package kr.sedanji.publication;

import com.fasterxml.jackson.databind.JsonNode;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/mentions/{id}")
public class PublicationController {
    private final PublicationQueryService query;
    private final PublicationFacade facade;
    public PublicationController(PublicationQueryService query,PublicationFacade facade) {this.query=query;this.facade=facade;}
    private long id(String id) {
        try {if(!id.matches("[0-9]+"))throw PublicationException.invalid();long n=Long.parseLong(id);if(n<1)throw PublicationException.invalid();return n;}
        catch(NumberFormatException e) {throw PublicationException.invalid();}
    }
    @GetMapping("/publication")
    public JsonNode get(@PathVariable String id,Principal user) {return query.get(id(id),user.getName());}
    @PostMapping("/suspend")
    public JsonNode suspend(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false) String key,Principal user) {
        return facade.write(id(id),false,body,key,user.getName());
    }
    @PostMapping("/restore")
    public JsonNode restore(@PathVariable String id,@RequestBody JsonNode body,@RequestHeader(value="Idempotency-Key",required=false) String key,Principal user) {
        return facade.write(id(id),true,body,key,user.getName());
    }
}
