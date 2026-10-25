package com.company.leave.leave;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Schema(name = "Comment", description = "A discussion comment on a leave request")
@Value
@Builder
public class Comment {

    long id;
    long requestId;
    long authorId;
    String authorUsername;
    String body;
    Instant createdAt;
}
