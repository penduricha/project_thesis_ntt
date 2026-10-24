package com.example.be_web_admin.repository.jpa;

import com.example.be_web_admin.model.jpa.Admin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminRepository extends JpaRepository<Admin,Long> {

    Admin findAdminByAdminId(Long adminId);
}
