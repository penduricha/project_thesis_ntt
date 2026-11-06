<?php
/**
 * Demo PHP (Moodle) gọi REST API GET lấy danh sách Admin từ Spring Boot
 */
function fetchAllAdminsFromSpring() {
    $url = "http://localhost:8080/api/admin/all";

    // Khởi tạo cURL
    $ch = curl_init($url);

    // Cấu hình các tùy chọn cURL
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true); // Trả về kết quả dạng chuỗi thay vì in trực tiếp
    curl_setopt($ch, CURLOPT_HTTPHEADER, [
        'Content-Type: application/json',
        'Accept: application/json'
    ]);
    curl_setopt($ch, CURLOPT_TIMEOUT, 10); // Timeout sau 10 giây nếu Spring Boot không phản hồi

    // Thực thi request
    $response = curl_exec($ch);
    $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);

    // Kiểm tra lỗi cURL
    if (curl_errno($ch)) {
        $error_msg = curl_error($ch);
        curl_close($ch);
        return ["status" => false, "message" => "cURL Error: " . $error_msg];
    }

    curl_close($ch);

    // Kiểm tra mã HTTP Status code từ Spring Boot trả về
    if ($httpCode === 200) {
        // Chuyển đổi JSON string từ Java thành mảng (Array) PHP
        $admins = json_decode($response, true);
        return ["status" => true, "data" => $admins];
    } else {
        return ["status" => false, "message" => "Server returned HTTP code: " . $httpCode];
    }
}

// --- THỰC THI THỬ ---
$result = fetchAllAdminsFromSpring();

if ($result['status']) {
    echo "<h3>Lấy danh sách Admin thành công từ Spring Boot:</h3>";
    echo "<pre>";
    print_r($result['data']);
    echo "</pre>";
} else {
    echo "<p style='color:red;'>Lỗi: " . $result['message'] . "</p>";
}
?>