package com.company.leave.approvals;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "Comment", description = "A comment reconstructed from the immutable request history")
@Value
@Builder
public class ApprovalComment {

    long id;
    long requestId;
    long authorId;
    String authorUsername;
    String body;
    Instant createdAt;
}
