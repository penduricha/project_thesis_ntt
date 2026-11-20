package com.example.be_server_ai.repository.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.be_server_ai.model.jpa.IssueType;

/** Bảng ntt_issue_types (6 dòng seed, chỉ đọc). Dùng để đưa danh sách issue_type hợp lệ vào schema của prompt. */
public interface IssueTypeRepository extends JpaRepository<IssueType, String> {
}
