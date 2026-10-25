package com.company.leave.leave;

import jakarta.validation.constraints.NotBlank;
import lombok.Value;

@Value
public class CommentCreateRequest {

    @NotBlank
    String body;
}
