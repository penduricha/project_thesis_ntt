package com.example.be_web_admin.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** memberIds không gồm chính người tạo; tổng thành viên (cả người tạo) phải từ 3. */
public record CreateGroupRequest(
        @NotBlank String name,
        @NotNull List<Long> memberIds) {
}
