package com.example.be_web_admin;

import com.example.be_web_admin.data_init.DataInitializer;
import com.example.be_web_admin.enums.AccountStatus;
import com.example.be_web_admin.enums.Role;
import com.example.be_web_admin.model.jpa.Admin;
import com.example.be_web_admin.model.jpa.Course;
import com.example.be_web_admin.repository.jpa.AdminRepository;
import com.example.be_web_admin.repository.jpa.CourseRepository;
import com.example.be_web_admin.security.password.MgmtPassword;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerTest {

    private static TransactionTemplate noopTxTemplate() {
        PlatformTransactionManager tm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        return new TransactionTemplate(tm);
    }

    @Test
    @SuppressWarnings("unchecked")
    void seedsTenActiveAdminsWhenAdminTableIsEmptyEvenIfCourseExists() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AdminRepository adminRepo = mock(AdminRepository.class);
        CourseRepository courseRepo = mock(CourseRepository.class);
        MgmtPassword mgmtPassword = new MgmtPassword();

        when(adminRepo.count()).thenReturn(0L);
        Course existingCourse1 = new Course();
        existingCourse1.setCourseId(8480201L);
        when(courseRepo.findCourseByCourseId(8480201L)).thenReturn(existingCourse1);
        when(courseRepo.findCourseByCourseId(8480202L)).thenReturn(null);
        when(courseRepo.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicReference<List<Admin>> savedRef = new AtomicReference<>();
        when(adminRepo.saveAll(anyIterable())).thenAnswer(inv -> {
            List<Admin> list = (List<Admin>) inv.getArgument(0);
            savedRef.set(list);
            return list;
        });

        DataInitializer init = new DataInitializer(
                jdbc, adminRepo, courseRepo, mgmtPassword, noopTxTemplate(), true, "TestSeed@2026");
        init.initializeData();

        List<Admin> saved = savedRef.get();
        assertEquals(10, saved.size());
        assertTrue(saved.stream().allMatch(a -> a.getAccountStatus() == AccountStatus.ACTIVE));
        assertTrue(saved.stream().allMatch(a -> Boolean.FALSE.equals(a.getTotpEnabled())));
        assertEquals(8, saved.stream().filter(a -> a.getRole() == Role.ADMIN).count());
        assertEquals(2, saved.stream().filter(a -> a.getRole() == Role.LECTURER).count());
        assertFalse(saved.stream().anyMatch(a -> a.getCourse() == null));
        assertTrue(existingCourse1.getAdmins().isEmpty());
        verify(courseRepo).save(any(Course.class)); // chỉ tạo course 8480202 vì 8480201 đã có
    }

    @Test
    void skipsWhenAdminTableAlreadyHasRecords() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AdminRepository adminRepo = mock(AdminRepository.class);
        CourseRepository courseRepo = mock(CourseRepository.class);
        when(adminRepo.count()).thenReturn(1L);

        DataInitializer init = new DataInitializer(
                jdbc, adminRepo, courseRepo, new MgmtPassword(), noopTxTemplate(), true, "TestSeed@2026");
        init.initializeData();

        verify(courseRepo, never()).save(any());
        verify(adminRepo, never()).saveAll(anyIterable());
    }
}
