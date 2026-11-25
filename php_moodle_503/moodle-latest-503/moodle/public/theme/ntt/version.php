<?php
// This file is part of Moodle - http://moodle.org/
//
// Moodle is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Moodle is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Moodle.  If not, see <http://www.gnu.org/licenses/>.

/**
 * Theme NTT: giao diện của đề án, kế thừa Boost. Chỉ có SCSS, phông chữ và cấu hình; không đổi nghiệp vụ của Moodle.
 *
 * @package    theme_ntt
 * @copyright  2026 NTT
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

$plugin->version   = 2026112500;
$plugin->requires  = 2026100200; // Cùng mốc với theme_boost của bản Moodle 5.3 trong repo.
$plugin->component = 'theme_ntt';
$plugin->dependencies = [
    'theme_boost' => ANY_VERSION,
];
