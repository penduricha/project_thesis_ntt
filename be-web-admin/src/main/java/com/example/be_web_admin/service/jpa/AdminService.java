package com.example.be_web_admin.service.jpa;

import com.example.be_web_admin.model.jpa.Admin;
import org.springframework.orm.jpa.JpaSystemException;

import java.util.List;
import java.util.Map;

public interface AdminService {

    public boolean addAdmin(Admin admin, Long courseId) throws JpaSystemException;

    public Object loginAdmin(Long adminId, String password) throws Exception;

    public Map<String, Object> getAdminInfo(Long adminId) throws Exception;

    public Map<String, Object> getAdminDetailInformation(Long adminId) throws Exception;

    public List<Admin> getAllAdmins() throws JpaSystemException;

    public String getAdminNameByAdminId(Long adminId) throws JpaSystemException;
}
