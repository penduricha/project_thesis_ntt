// Theme NTT - cho phép thu gọn node cấp 1 của block Navigation / Settings.
//
// Moodle cố tình gán data-collapsible="false" cho mọi node depth=1
// (blocks/navigation/renderer.php:166). core/tree đọc thuộc tính này trong
// isGroupCollapsible() rồi TỪ CHỐI collapse, nên mũi tên cạnh "Dashboard" /
// "Site pages" bấm không có tác dụng.
//
// Không sửa PHP của Moodle. Script này chỉ bỏ thuộc tính đó trong sự kiện click
// ở pha capture — chạy TRƯỚC handler delegate của core/tree (pha bubble) — nên
// core/tree tự thu gọn bằng logic gốc của nó. Cũng xử lý được node do AJAX nạp
// về sau vì không cần khởi tạo lại cây.
(function() {
    'use strict';

    var selector = '.block_navigation .block_tree [role="treeitem"][data-collapsible="false"],' +
        '.block_settings .block_tree [role="treeitem"][data-collapsible="false"]';

    document.addEventListener('click', function(event) {
        if (!event.target || !event.target.closest) {
            return;
        }
        var item = event.target.closest(selector);
        if (item) {
            item.removeAttribute('data-collapsible');
        }
    }, true); // capture: phải chạy trước handler click của core/tree.
}());
