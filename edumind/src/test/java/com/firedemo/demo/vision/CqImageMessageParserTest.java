package com.firedemo.demo.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CqImageMessageParserTest {

    private final CqImageMessageParser parser = new CqImageMessageParser();

    @Test
    void extractsAndDecodesImageUrl() {
        String message = "[CQ:image,file=a.png,url=https://multimedia.nt.qq.com.cn/download?appid=1&amp;fileid=2]这是什么？";

        assertEquals(
                "https://multimedia.nt.qq.com.cn/download?appid=1&fileid=2",
                parser.extractImageUrl(message).orElseThrow());
        assertEquals("这是什么？", parser.stripImages(message));
    }

    @Test
    void returnsEmptyWhenMessageHasNoImage() {
        assertTrue(parser.extractImageUrl("普通消息").isEmpty());
    }
}
