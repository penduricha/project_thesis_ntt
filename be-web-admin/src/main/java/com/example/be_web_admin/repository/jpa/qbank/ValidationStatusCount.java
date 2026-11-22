package com.example.be_web_admin.repository.jpa.qbank;

import com.example.be_web_admin.enums.qbank.ValidationStatusD;

/** Projection cho biểu đồ tròn "trạng thái kiểm định" ở màn Dashboard. */
public interface ValidationStatusCount {

    ValidationStatusD getStatus();

    long getTotal();
}
