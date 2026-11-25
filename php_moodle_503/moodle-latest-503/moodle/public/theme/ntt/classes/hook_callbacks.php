<?php
// This file is part of Moodle - http://moodle.org/
//
// Moodle is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.

/**
 * Theme NTT hook callbacks: nạp JS bổ trợ cho treeview của block Navigation.
 *
 * Chỉ nạp tài sản của theme; không thay đổi logic PHP của Moodle.
 *
 * @package    theme_ntt
 * @copyright  2026 NTT
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

namespace theme_ntt;

defined('MOODLE_INTERNAL') || die();

/**
 * Hook callbacks của theme.
 */
class hook_callbacks {

    /**
     * Nạp script cho phép thu gọn node cấp 1 của block Navigation / Settings.
     *
     * @param \core\hook\output\before_standard_top_of_body_html_generation $hook
     */
    public static function before_standard_top_of_body_html_generation(
        \core\hook\output\before_standard_top_of_body_html_generation $hook
    ): void {
        global $PAGE;

        $PAGE->requires->js('/theme/ntt/js/tree-collapsible.js');
    }
}
