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
 * Trang "Nhóm chat": tạo nhóm, đổi tên, thêm/xoá thành viên, rời / giải tán nhóm và bắt đầu chat đơn.
 *
 * Mọi thao tác gọi web service của plugin (chỉ tác động lên chính người đang đăng nhập); quyền thật do máy chủ kiểm tra,
 * JS chỉ ẩn/hiện nút cho dễ dùng.
 *
 * @module     local_nttchat/groups
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

import Ajax from 'core/ajax';
import Notification from 'core/notification';
import Templates from 'core/templates';
import {getString} from 'core/str';
// Lưu ý: ngăn kéo đọc tham số chữ thường ({conversationid}, {userid}) dù JSDoc của helper ghi camelCase.
import * as DrawerHelper from 'core_message/message_drawer_helper';

/** Chờ gõ xong rồi mới tìm (mili-giây). */
const SEARCH_DELAY = 300;

/** Số ký tự tối thiểu để tìm. */
const SEARCH_MIN = 2;

const SELECTORS = {
    ROOT: '[data-region="nttchat-page"]',
    GROUPS: '[data-region="groups"]',
    EMPTY: '[data-region="empty"]',
    CREATE: '[data-region="create"]',
    CREATE_NAME: '[data-region="create-name"]',
    SELECTED: '[data-region="selected"]',
    SEARCH_INPUT: '[data-region="search-input"]',
    SEARCH_RESULTS: '[data-region="search-results"]',
    GROUP: '[data-region="group"]',
    MANAGE: '[data-region="manage"]',
    RENAME_INPUT: '[data-region="rename-input"]',
};

/** @type {Object[]} Danh sách nhóm lần tải gần nhất. */
let groups = [];

/** @type {Map<Number,String>} Thành viên đã chọn trong form tạo nhóm: id => họ tên. */
const selected = new Map();

/** @type {Set<Number>} Nhóm đang mở khung quản lý (giữ nguyên khi vẽ lại danh sách). */
const openManage = new Set();

/**
 * Gọi một hàm web service của plugin.
 *
 * @param {String} name Tên hàm không kèm tiền tố "local_nttchat_".
 * @param {Object} args
 * @returns {Promise<Object>}
 */
const call = (name, args = {}) => Ajax.call([{methodname: `local_nttchat_${name}`, args}])[0];

/**
 * Hoãn gọi hàm cho tới khi ngừng gõ.
 *
 * @param {Function} fn
 * @param {Number} wait
 * @returns {Function}
 */
const debounce = (fn, wait) => {
    let timeout = null;
    return (...args) => {
        clearTimeout(timeout);
        timeout = setTimeout(() => fn(...args), wait);
    };
};

/**
 * Vẽ lại danh sách nhóm từ dữ liệu máy chủ.
 *
 * @param {HTMLElement} root
 */
const loadGroups = async(root) => {
    const result = await call('get_groups');
    groups = result.groups;
    const target = root.querySelector(SELECTORS.GROUPS);
    const {html, js} = await Templates.renderForPromise('local_nttchat/groups_list', {groups});
    Templates.replaceNodeContents(target, html, js);
    root.querySelector(SELECTORS.EMPTY).classList.toggle('d-none', groups.length > 0);
    openManage.forEach((id) => {
        const card = target.querySelector(`${SELECTORS.GROUP}[data-groupid="${id}"]`);
        if (card) {
            setManageOpen(card, true);
        } else {
            openManage.delete(id);
        }
    });
};

/**
 * Mở hoặc đóng khung quản lý của một nhóm.
 *
 * @param {HTMLElement} card
 * @param {Boolean} open
 */
const setManageOpen = (card, open) => {
    const panel = card.querySelector(SELECTORS.MANAGE);
    const toggle = card.querySelector('[data-action="toggle-manage"]');
    if (!panel) {
        return;
    }
    panel.classList.toggle('d-none', !open);
    if (toggle) {
        toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    }
    const id = Number(card.dataset.groupid);
    if (open) {
        openManage.add(id);
    } else {
        openManage.delete(id);
    }
};

/**
 * Vẽ các "chip" thành viên đã chọn trong form tạo nhóm.
 *
 * @param {HTMLElement} root
 */
const renderSelected = async(root) => {
    const target = root.querySelector(SELECTORS.SELECTED);
    target.textContent = '';
    if (selected.size === 0) {
        const none = document.createElement('span');
        none.className = 'text-muted small';
        none.textContent = await getString('nomembersselected', 'local_nttchat');
        target.appendChild(none);
        return;
    }
    const removeLabel = await getString('remove', 'local_nttchat');
    selected.forEach((fullname, id) => {
        const chip = document.createElement('span');
        chip.className = 'nttchat-chip';
        const name = document.createElement('span');
        name.textContent = fullname;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-link p-0';
        button.dataset.action = 'unselect-user';
        button.dataset.userid = String(id);
        button.setAttribute('aria-label', `${removeLabel}: ${fullname}`);
        button.textContent = '×';
        chip.append(name, button);
        target.appendChild(chip);
    });
};

/**
 * Tìm người theo từ khoá và vẽ kết quả ngay dưới ô nhập.
 *
 * @param {HTMLInputElement} input
 */
const search = async(input) => {
    const results = input.parentElement.querySelector(SELECTORS.SEARCH_RESULTS);
    const query = input.value.trim();
    // Bỏ qua kết quả của lần tìm cũ nếu người dùng đã gõ tiếp.
    const ticket = String(Date.now()) + Math.random();
    input.dataset.ticket = ticket;
    if (query.length < SEARCH_MIN) {
        results.textContent = '';
        return;
    }
    const mode = input.dataset.search;
    const {users} = await call('search_users', {query});
    if (input.dataset.ticket !== ticket) {
        return;
    }

    let visible = users;
    let action = 'direct-message';
    let labelKey = 'sendmessage';
    if (mode === 'create') {
        visible = users.filter((user) => !selected.has(user.id));
        action = 'select-user';
        labelKey = 'add';
    } else if (mode === 'manage') {
        const group = groups.find((item) => item.id === Number(input.dataset.groupid));
        const memberIds = new Set(group ? group.members.map((member) => member.id) : []);
        visible = users.filter((user) => !memberIds.has(user.id));
        action = 'add-member';
        labelKey = 'add';
    }
    const label = await getString(labelKey, 'local_nttchat');
    const {html, js} = await Templates.renderForPromise('local_nttchat/user_results', {action, label, users: visible});
    Templates.replaceNodeContents(results, html, js);
};

/**
 * Hộp xác nhận rồi chạy hành động.
 *
 * @param {String} titleKey Khoá chuỗi làm tiêu đề và nhãn nút.
 * @param {String} questionKey Khoá chuỗi câu hỏi.
 * @param {*} questionParam Tham số của câu hỏi.
 * @param {Function} action
 */
const confirmThen = async(titleKey, questionKey, questionParam, action) => {
    const [title, question] = await Promise.all([
        getString(titleKey, 'local_nttchat'),
        getString(questionKey, 'local_nttchat', questionParam),
    ]);
    Notification.deleteCancel(title, question, title, action);
};

/**
 * Chạy một thao tác và báo lỗi bằng hộp thoại chuẩn của Moodle.
 *
 * @param {Function} work
 */
const safely = async(work) => {
    try {
        await work();
    } catch (error) {
        Notification.exception(error);
    }
};

/**
 * Xử lý mọi nút bấm trong trang (uỷ quyền sự kiện).
 *
 * @param {HTMLElement} root
 * @param {Event} e
 */
const onClick = (root, e) => {
    const button = e.target.closest('[data-action]');
    if (!button || !root.contains(button)) {
        return;
    }
    const action = button.dataset.action;
    const card = button.closest(SELECTORS.GROUP);
    const groupId = card ? Number(card.dataset.groupid) : 0;
    const userId = Number(button.dataset.userid || 0);

    switch (action) {
        case 'toggle-create': {
            const form = root.querySelector(SELECTORS.CREATE);
            const open = form.classList.contains('d-none');
            form.classList.toggle('d-none', !open);
            root.querySelectorAll('[data-action="toggle-create"]').forEach((el) => {
                el.setAttribute('aria-expanded', open ? 'true' : 'false');
            });
            if (open) {
                form.querySelector(SELECTORS.CREATE_NAME).focus();
            }
            break;
        }
        case 'select-user':
            selected.set(userId, button.dataset.fullname);
            button.closest('li').remove();
            safely(() => renderSelected(root));
            break;
        case 'unselect-user':
            selected.delete(userId);
            safely(() => renderSelected(root));
            break;
        case 'submit-create':
            safely(async() => {
                const name = root.querySelector(SELECTORS.CREATE_NAME).value.trim();
                if (selected.size === 0) {
                    throw new Error(await getString('needmembers', 'local_nttchat'));
                }
                const result = await call('create_group', {name, memberids: [...selected.keys()]});
                selected.clear();
                root.querySelector(SELECTORS.CREATE_NAME).value = '';
                root.querySelector(`${SELECTORS.CREATE} ${SELECTORS.SEARCH_INPUT}`).value = '';
                root.querySelector(`${SELECTORS.CREATE} ${SELECTORS.SEARCH_RESULTS}`).textContent = '';
                root.querySelector(SELECTORS.CREATE).classList.add('d-none');
                root.querySelectorAll('[data-action="toggle-create"]').forEach((el) => el.setAttribute('aria-expanded', 'false'));
                await renderSelected(root);
                await loadGroups(root);
                DrawerHelper.showConversation({conversationid: result.id});
            });
            break;
        case 'direct-message':
            DrawerHelper.createConversationWithUser({userid: userId});
            break;
        case 'open-chat':
            DrawerHelper.showConversation({conversationid: groupId});
            break;
        case 'toggle-manage':
            setManageOpen(card, card.querySelector(SELECTORS.MANAGE).classList.contains('d-none'));
            break;
        case 'rename':
            safely(async() => {
                await call('rename_group', {groupid: groupId, name: card.querySelector(SELECTORS.RENAME_INPUT).value.trim()});
                await loadGroups(root);
            });
            break;
        case 'add-member':
            safely(async() => {
                await call('add_members', {groupid: groupId, userids: [userId]});
                await loadGroups(root);
            });
            break;
        case 'remove-member':
            confirmThen('remove', 'removeconfirm', button.dataset.fullname, () => safely(async() => {
                await call('remove_member', {groupid: groupId, userid: userId});
                await loadGroups(root);
            }));
            break;
        case 'leave':
            confirmThen('leavegroup', 'leaveconfirm', null, () => safely(async() => {
                await call('leave_group', {groupid: groupId});
                openManage.delete(groupId);
                await loadGroups(root);
            }));
            break;
        case 'delete':
            confirmThen('deletegroup', 'deleteconfirm', null, () => safely(async() => {
                await call('delete_group', {groupid: groupId});
                openManage.delete(groupId);
                await loadGroups(root);
            }));
            break;
        default:
            break;
    }
};

/**
 * Khởi động trang.
 */
export const init = () => {
    const root = document.querySelector(SELECTORS.ROOT);
    if (!root) {
        return;
    }
    root.addEventListener('click', (e) => onClick(root, e));
    root.addEventListener('input', debounce((e) => {
        if (e.target.matches(SELECTORS.SEARCH_INPUT)) {
            safely(() => search(e.target));
        }
    }, SEARCH_DELAY));
    safely(async() => {
        await renderSelected(root);
        await loadGroups(root);
    });
};
