package com.example.be_web_admin.repository.jpa.ai;

import com.example.be_web_admin.model.jpa.ai.IssueType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface IssueTypeRepository extends JpaRepository<IssueType, String> {

    List<IssueType> findAllByOrderBySortOrderAsc();

    /** Chỉ các loại lỗi này mới được tính P/R/F1 ở Chương 4. */
    List<IssueType> findByIsEvaluatedTrueOrderBySortOrderAsc();
}
