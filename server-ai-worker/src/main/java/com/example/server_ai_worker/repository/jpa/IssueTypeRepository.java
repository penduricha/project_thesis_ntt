package com.example.server_ai_worker.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.server_ai_worker.model.jpa.IssueType;

public interface IssueTypeRepository extends JpaRepository<IssueType, String> {

    List<IssueType> findAllByOrderBySortOrderAscIssueTypeAsc();
}
