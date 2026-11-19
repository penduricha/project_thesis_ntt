package com.example.be_web_admin.chat.dto;

import jakarta.validation.constraints.NotNull;

public record CreateDirectRequest(@NotNull Long peerId) {
}
