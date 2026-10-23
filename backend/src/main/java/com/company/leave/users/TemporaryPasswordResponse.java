package com.company.leave.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(description = "Server-generated temporary password returned by POST /users/{userId}/reset-password (FR20)")
@Value
@Builder
public class TemporaryPasswordResponse {
    @Schema(description = "The generated temporary password the user must change at next login",
            example = "Temp#8841")
    String temporaryPassword;
}
