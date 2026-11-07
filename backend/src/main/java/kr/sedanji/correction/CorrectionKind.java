package kr.sedanji.correction;

public enum CorrectionKind {
    MENTION("mentions","mention-corrections","mention_drafts","target_mention_id"),
    RESTAURANT("restaurants","restaurant-revisions","restaurant_revisions","target_id"),
    CHEF("chefs","chef-revisions","chef_revisions","target_id");
    final String targets, route, table, targetColumn;
    CorrectionKind(String targets,String route,String table,String targetColumn) {
        this.targets=targets; this.route=route; this.table=table; this.targetColumn=targetColumn;
    }
    static CorrectionKind path(String path) {
        for (var kind:values()) if (path.startsWith("/api/admin/"+kind.targets+"/") || path.startsWith("/api/admin/"+kind.route+"/")) return kind;
        throw CorrectionException.invalid();
    }
    String predicate() { return this==MENTION ? " and kind='CORRECTION'" : ""; }
}
