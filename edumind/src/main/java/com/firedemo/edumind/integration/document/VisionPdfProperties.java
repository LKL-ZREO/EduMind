package com.firedemo.edumind.integration.document;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "vision-pdf")
public class VisionPdfProperties {

    private int concurrency = 10;

    private boolean maintainFormat = false;

    private int maxPdfPages = 50;

    private int contextTailChars = 500;
}
