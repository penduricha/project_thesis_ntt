package com.example.be_web_admin.service.jpa.impl;

import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.service.jpa.AdminService;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dịch vụ truy vấn/quản lý tài khoản admin (dùng bởi AdminControllerMgmtWeb / AdminControllerMgmtMobile).
 * Luồng đăng nhập (mật khẩu + TOTP + token) nằm ở {@link AdminLoginServiceImpl}, không nằm ở đây.
 */
@Service
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final CourseRepository courseRepository;

    public AdminServiceImpl(AdminRepository adminRepository, CourseRepository courseRepository) {
        this.adminRepository = adminRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public boolean addAdmin(Admin admin, Long courseId) throws JpaSystemException {
        Course courseFound = courseRepository.findCourseByCourseId(courseId);
        if (courseFound != null) {
            courseFound.getAdmins().add(admin);
            admin.setCourse(courseFound);
            adminRepository.save(admin);
            return true;
        }
        return false;
    }

    @Override
    public Map<String, Object> getAdminInfo(Long adminId) throws JpaSystemException {
        Map<String, Object> responseData = new HashMap<>();
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            responseData.put("adminId", adminFound.getAdminId());
            responseData.put("adminName", adminFound.getAdminName());
            responseData.put("gender", adminFound.getGender());
            responseData.put("dateOfBirthAdmin", adminFound.getDateOfBirthAdmin());
            // Vai trò để giao diện ẩn/hiện menu theo quyền.
            // Cột ntt_admins.role là ENUM('ADMIN','LECTURER'); trả nguyên tên hằng để phía
            // Vue ánh xạ 1-1, không phải đoán chuỗi. Trước đây trường này bị thiếu nên
            // QBank Console luôn mặc định 'admin' và hiện mọi mục cho cả giảng viên.
            if (adminFound.getRole() != null) {
                responseData.put("role", adminFound.getRole().name());
            }
        }
        return responseData;
    }

    @Override
    public Map<String, Object> getAdminDetailInformation(Long adminId) throws JpaSystemException {
        Map<String, Object> responseData = new HashMap<>();
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            responseData.put("adminId", adminFound.getAdminId());
            responseData.put("address", adminFound.getAddress());
            responseData.put("idCard", adminFound.getIdCard());
            // Course có thể null (admin chưa gán khóa học): chỉ trả riêng thông tin admin, không văng 500.
            Course course = adminFound.getCourse();
            if (course != null) {
                responseData.put("industry", course.getIndustry());
                responseData.put("faculty", course.getFaculty());
                responseData.put("trainingSystem", course.getTrainingSystem());
                responseData.put("typeOfTraining", course.getTypeOfTraining());
                responseData.put("major", course.getMajor());
                responseData.put("branch", course.getBranch());
            }
        }
        return responseData;
    }

    @Override
    public List<Admin> getAllAdmins() throws JpaSystemException {
        return adminRepository.findAll();
    }

    @Override
    public String getAdminNameByAdminId(Long adminId) throws JpaSystemException {
        Admin adminFound = adminRepository.findAdminByAdminId(adminId);
        if (adminFound != null) {
            return adminFound.getAdminName();
        }
        return null;
    }
}
