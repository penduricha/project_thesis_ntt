package com.example.be_web_admin.controller.web;

import com.example.be_web_admin.dto.qbank.ActivityItem;
import com.example.be_web_admin.dto.qbank.DashboardSummary;
import com.example.be_web_admin.service.qbank.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Dashboard: 1 request trả toàn bộ thẻ KPI + biểu đồ trạng thái kiểm định (cache Redis 30s),
 * 1 request trả "Hoạt động gần đây" của chính người đang đăng nhập.
 */
@RestController
@RequestMapping("/web/admin/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummary summary(@RequestParam(name = "subjectId", required = false) Long subjectId) {
        return dashboardService.summary(subjectId);
    }

    @GetMapping("/activity")
    public List<ActivityItem> activity(@RequestParam(name = "limit", required = false) Integer limit,
                                       Authentication authentication) {
        return dashboardService.activity(WebPrincipal.adminId(authentication), limit);
    }
}
