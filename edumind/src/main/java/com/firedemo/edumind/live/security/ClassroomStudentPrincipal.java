package com.firedemo.edumind.live.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

/** Identity carried by a short-lived token scoped to one live classroom. */
public record ClassroomStudentPrincipal(
        String studentId,
        String studentName,
        Long liveSessionId
) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return studentId;
    }
}
