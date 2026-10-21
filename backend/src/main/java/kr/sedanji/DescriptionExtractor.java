package kr.sedanji;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import static kr.sedanji.Models.*;

@Component
class DescriptionExtractor {
    private static final Pattern ADDRESS = Pattern.compile("(?:서울(?:특별시)?|부산(?:광역시)?|대구(?:광역시)?|인천(?:광역시)?|광주(?:광역시)?|대전(?:광역시)?|울산(?:광역시)?|세종(?:특별자치시)?|경기(?:도)?|강원(?:특별자치도|도)?|충청[남북]도|전라[남북]도|경상[남북]도|제주(?:특별자치도)?)\\s+.+(?:로|길|동|리)\\s*\\d[0-9-]*(?:\\s.*)?");
    private static final Pattern NAMED = Pattern.compile("(?:중식당|식당명|상호|식당)\\s*[:：]?\\s*\\[([^]\\r\\n]{1,80})]");

    static String videoId(String url) {
        try {
            URI uri = URI.create(url);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getUserInfo() != null) throw new IllegalArgumentException();
            String host = Optional.ofNullable(uri.getHost()).orElse("").toLowerCase(Locale.ROOT);
            String id = "";
            if (host.equals("youtu.be")) id = uri.getPath().substring(1);
            else if (Set.of("youtube.com", "www.youtube.com", "m.youtube.com").contains(host)) {
                if (uri.getPath().equals("/watch")) {
                    List<String> ids = Arrays.stream(Optional.ofNullable(uri.getRawQuery()).orElse("").split("&"))
                        .filter(s -> s.startsWith("v=")).map(s -> URLDecoder.decode(s.substring(2), StandardCharsets.UTF_8)).toList();
                    if (ids.size() == 1) id = ids.get(0);
                } else if (uri.getPath().matches("/(shorts|embed|live)/[A-Za-z0-9_-]{11}")) id = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
            }
            if (!id.matches("[A-Za-z0-9_-]{11}")) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException ex) { throw new IllegalArgumentException("올바른 YouTube 영상 URL을 입력하세요."); }
    }

    List<Extracted> extract(String description) {
        String[] lines = description.split("\\R", -1);
        List<Extracted> result = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            var address = ADDRESS.matcher(lines[i]);
            if (i > 0 && address.find()) {
                String name = lines[i - 1].replaceAll("^[^가-힣A-Za-z0-9]+", "").replaceAll("[\\[\\]]", "").strip();
                if (!name.isBlank() && name.length() <= 80 && !name.contains("http") && !name.startsWith("#"))
                    result.add(new Extracted(name, address.group(), i, lines[i - 1] + "\n" + lines[i]));
            }
            var named = NAMED.matcher(lines[i]);
            while (named.find()) {
                String name = named.group(1).strip();
                if (!name.isEmpty() && result.stream().noneMatch(e -> e.name().equals(name)))
                    result.add(new Extracted(name, "", i + 1, lines[i]));
            }
        }
        return result;
    }
}
