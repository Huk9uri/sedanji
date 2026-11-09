package kr.sedanji.publicweb;

import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import static kr.sedanji.publicweb.PublicResult.*;

@Repository
public class PublicQueryRepository {
    private final NamedParameterJdbcTemplate db;
    private static final String PUBLIC = "m.verification_status='APPROVED' AND m.publication_status='PUBLISHED'";
    public PublicQueryRepository(NamedParameterJdbcTemplate db) { this.db=db; }
    static String areaSql(boolean chef) {
        return "SELECT r.* FROM restaurants r WHERE r.longitude BETWEEN :w AND :e AND r.latitude BETWEEN :s AND :n"
            + " AND EXISTS (SELECT 1 FROM mentions m WHERE m.restaurant_id=r.id AND " + PUBLIC
            + (chef ? " AND m.chef_id=:chef" : "") + ") ORDER BY r.id ASC LIMIT 201";
    }
    public Area area(PublicInput.Bounds q) {
        var params = new MapSqlParameterSource().addValue("w",q.west()).addValue("s",q.south())
            .addValue("e",q.east()).addValue("n",q.north()).addValue("chef",q.chefId());
        var rows = db.query(areaSql(q.chefId()!=null), params,
            (rs,i) -> new Item(rs.getLong("id"),rs.getString("name"),rs.getDouble("latitude"),rs.getDouble("longitude"),
                rs.getString("road_address"),rs.getString("address"),List.of(),0,false));
        boolean truncated=rows.size()>200;
        var chosen=rows.subList(0,Math.min(rows.size(),200));
        var previews=new HashMap<Long,List<Chef>>(); var counts=new HashMap<Long,Long>();
        if (!chosen.isEmpty()) db.query("""
            SELECT * FROM (
              SELECT m.restaurant_id,c.id,c.name,
                count(*) OVER (PARTITION BY m.restaurant_id) AS chef_count,
                row_number() OVER (PARTITION BY m.restaurant_id ORDER BY c.id) AS position
              FROM mentions m JOIN chefs c ON c.id=m.chef_id WHERE m.restaurant_id IN (:ids) AND
            """ + PUBLIC + " GROUP BY m.restaurant_id,c.id,c.name) ranked WHERE position<=3 ORDER BY restaurant_id,id",
            Map.of("ids",chosen.stream().map(Item::id).toList()), rs -> {
                long id=rs.getLong("restaurant_id"); counts.put(id,rs.getLong("chef_count"));
                previews.computeIfAbsent(id,k->new ArrayList<>()).add(new Chef(rs.getLong("id"),rs.getString("name")));
            });
        var items=chosen.stream().map(r->new Item(r.id(),r.name(),r.latitude(),r.longitude(),r.roadAddress(),r.address(),
            previews.getOrDefault(r.id(),List.of()),counts.getOrDefault(r.id(),0L),counts.getOrDefault(r.id(),0L)>3)).toList();
        return new Area(items,new Query(q.west(),q.south(),q.east(),q.north(),q.chefId()),items.size(),200,truncated,null);
    }
    public Page<ChefItem> chefs(PublicInput.Page q) {
        String search=q.query().replace("!","!!").replace("%","!%").replace("_","!_");
        var p=new MapSqlParameterSource().addValue("q","%"+search+"%").addValue("size",q.size()).addValue("offset",(long)q.page()*q.size());
        String from=" FROM chefs c JOIN mentions m ON m.chef_id=c.id WHERE "+PUBLIC+" AND lower(c.name) LIKE lower(:q) ESCAPE '!'";
        long count=db.queryForObject("SELECT count(DISTINCT c.id)"+from,p,Long.class);
        var items=db.query("SELECT c.id,c.name,count(DISTINCT m.restaurant_id) AS restaurant_count"+from
            +" GROUP BY c.id,c.name ORDER BY restaurant_count DESC,c.name COLLATE \"C\" ASC,c.id ASC LIMIT :size OFFSET :offset",p,
            (rs,i)->new ChefItem(rs.getLong("id"),rs.getString("name"),rs.getLong("restaurant_count")));
        return new Page<>(items,count,q.page(),q.size(),((long)q.page()+1)*q.size()<count);
    }
    public Detail detail(long id, PublicInput.Page q) {
        var places=db.query("SELECT r.* FROM restaurants r WHERE r.id=:id AND EXISTS (SELECT 1 FROM mentions m WHERE m.restaurant_id=r.id AND "+PUBLIC+")",
            Map.of("id",id),(rs,i)->new Detail(rs.getLong("id"),rs.getString("name"),rs.getString("kakao_place_id"),rs.getString("road_address"),rs.getString("address"),
                rs.getDouble("latitude"),rs.getDouble("longitude"),rs.getString("place_url"),null));
        if (places.isEmpty()) throw new PublicInput.Failure(404,"NOT_FOUND","공개된 식당을 찾을 수 없습니다.");
        var p=Map.of("id",id,"size",q.size(),"offset",(long)q.page()*q.size());
        long count=db.queryForObject("SELECT count(*) FROM mentions m WHERE m.restaurant_id=:id AND "+PUBLIC,p,Long.class);
        var sources=db.query("SELECT m.id,m.chef_id,c.name,m.mention_type,m.timestamp_seconds,v.id AS video_id,v.title,v.channel_title,v.published_at"
            +" FROM mentions m JOIN chefs c ON c.id=m.chef_id JOIN videos v ON v.id=m.video_id WHERE m.restaurant_id=:id AND "+PUBLIC
            +" ORDER BY m.id ASC LIMIT :size OFFSET :offset",p,(rs,i)->new Source(rs.getLong("id"),new Chef(rs.getLong("chef_id"),rs.getString("name")),
                rs.getString("mention_type"),new Video(rs.getString("video_id"),rs.getString("title"),rs.getString("channel_title"),rs.getObject("published_at",OffsetDateTime.class)),rs.getObject("timestamp_seconds",Integer.class)));
        var r=places.get(0);
        return new Detail(r.id(),r.name(),r.kakaoPlaceId(),r.roadAddress(),r.address(),r.latitude(),r.longitude(),r.placeUrl(),
            new Page<>(sources,count,q.page(),q.size(),((long)q.page()+1)*q.size()<count));
    }
}
