package kr.sedanji.video;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class VideoUrl {
    private VideoUrl() {}
    public static String parse(String url) {
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

}
