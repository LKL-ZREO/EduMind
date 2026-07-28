package com.firedemo.demo.vision;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CqImageMessageParser {

    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[CQ:image,([^]]+)]");
    private static final Pattern URL_PATTERN = Pattern.compile("(?:^|,)url=([^,]+)");

    public Optional<String> extractImageUrl(String message) {
        if (message == null || message.isBlank()) return Optional.empty();
        Matcher imageMatcher = IMAGE_PATTERN.matcher(message);
        if (!imageMatcher.find()) return Optional.empty();

        Matcher urlMatcher = URL_PATTERN.matcher(imageMatcher.group(1));
        if (!urlMatcher.find()) return Optional.empty();
        return Optional.of(decodeCqValue(urlMatcher.group(1)));
    }

    public String stripImages(String message) {
        if (message == null) return "";
        return IMAGE_PATTERN.matcher(message).replaceAll("").trim();
    }

    private String decodeCqValue(String value) {
        return value
                .replace("&amp;", "&")
                .replace("&#44;", ",")
                .replace("&#91;", "[")
                .replace("&#93;", "]")
                .replace("\\u0026", "&");
    }
}
