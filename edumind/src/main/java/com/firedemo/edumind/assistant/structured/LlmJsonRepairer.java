package com.firedemo.edumind.assistant.structured;

import org.jsonrepairj.JsonRepair;
import org.springframework.stereotype.Component;

/** Isolates the third-party repair library from the structured-output pipeline. */
@Component
public class LlmJsonRepairer {

    public String repair(String malformedJson) {
        return JsonRepair.repairJson(malformedJson);
    }
}
