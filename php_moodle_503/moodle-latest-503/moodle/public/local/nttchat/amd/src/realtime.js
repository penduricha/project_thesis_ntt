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
 * Báo tin nhắn mới: hỏi hộp thư Redis của người dùng theo chu kỳ, hiện thông báo bấm được để mở chat
 * và cập nhật số hội thoại chưa đọc trên thanh trên.
 *
 * Mỗi lần hỏi "không có gì mới" chỉ tốn một lệnh Redis phía máy chủ (không đụng MariaDB). Tab bị ẩn thì tạm dừng
 * hỏi và hỏi ngay khi tab hiện lại, nên nhiều tab mở nền không làm tăng tải.
 *
 * @module     local_nttchat/realtime
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

import Ajax from 'core/ajax';
import Log from 'core/log';
import {add as addToast} from 'core/toast';
import {getString} from 'core/str';
import * as DrawerHelper from 'core_message/message_drawer_helper';

/** Số thông báo tối đa hiện cùng lúc sau một lần hỏi (phần còn lại gộp vào thông báo cuối). */
const MAX_TOASTS = 3;

/** Chờ tối đa khi lỗi liên tiếp (giây). */
const MAX_BACKOFF = 60;

/** Chờ khi máy chủ báo Redis không dùng được (giây): hỏi lại thưa để biết khi Redis sống lại. */
const UNAVAILABLE_WAIT = 60;

const SELECTORS = {
    COUNT_REGION: '[data-region="popover-region-messages"] [data-region="count-container"]',
    DRAWER: '[data-region="message-drawer"]',
    OPEN_LINK: '[data-action="nttchat-open-conversation"]',
};

let started = false;
let lastId = '';
let timer = null;
let failures = 0;
let busy = false;
let intervalMs = 4000;

/**
 * Thoát ký tự HTML (nội dung toast được dựng bằng HTML thô).
 *
 * @param {String} text
 * @returns {String}
 */
const escapeHtml = (text) => String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

/**
 * Hội thoại này đang mở trong ngăn kéo tin nhắn và đang nhìn thấy không.
 *
 * @param {Number} conversationId
 * @returns {Boolean}
 */
const isConversationOpen = (conversationId) => {
    if (document.hidden) {
        return false;
    }
    const drawer = document.querySelector(SELECTORS.DRAWER);
    if (!drawer || drawer.getClientRects().length === 0) {
        return false;
    }
    return drawer.querySelector(
        `[data-region="view-conversation"][data-conversation-id="${conversationId}"]`
    ) !== null;
};

/**
 * Ghi số hội thoại chưa đọc lên biểu tượng tin nhắn ở thanh trên.
 *
 * @param {Number} count
 */
const updateUnreadBadge = async(count) => {
    if (count < 0) {
        return;
    }
    const containers = document.querySelectorAll(SELECTORS.COUNT_REGION);
    if (containers.length === 0) {
        return;
    }
    const label = await getString('unreadconversations', 'core_message', count);
    containers.forEach((container) => {
        const visible = container.querySelector('[aria-hidden="true"]');
        const hidden = container.querySelector('.visually-hidden');
        if (visible) {
            visible.textContent = String(count);
        }
        if (hidden) {
            hidden.textContent = label;
        }
        container.classList.toggle('hidden', count === 0);
    });
};

/**
 * Hiện thông báo cho một hội thoại (lấy tin mới nhất của hội thoại đó).
 *
 * @param {Object} event
 */
const showToast = async(event) => {
    const title = event.type === 'group' ?
        await getString('newgroupmessage', 'local_nttchat', {from: event.fromname, group: event.groupname}) :
        await getString('newmessagefrom', 'local_nttchat', event.fromname);
    const html = `<button type="button" class="nttchat-toast-link" data-action="nttchat-open-conversation" ` +
        `data-conversationid="${Number(event.conversationid)}">` +
        `<strong>${escapeHtml(title)}</strong><br><span>${escapeHtml(event.text)}</span></button>`;
    await addToast(html, {type: 'info', autohide: true, delay: 8000, closeButton: true});
};

/**
 * Xử lý các tin mới: bỏ qua tin của hội thoại đang mở, mỗi hội thoại một thông báo, tối đa MAX_TOASTS.
 *
 * @param {Object[]} events Cũ trước.
 */
const handleEvents = async(events) => {
    const latest = new Map();
    events.forEach((event) => {
        latest.set(event.conversationid, event); // Tin sau ghi đè tin trước của cùng hội thoại.
    });
    const toShow = [...latest.values()].filter((event) => !isConversationOpen(event.conversationid));
    for (const event of toShow.slice(-MAX_TOASTS)) {
        await showToast(event);
    }
};

/**
 * Mở hội thoại trong ngăn kéo tin nhắn khi bấm vào thông báo.
 *
 * @param {Event} e
 */
const onClick = (e) => {
    const link = e.target.closest(SELECTORS.OPEN_LINK);
    if (!link) {
        return;
    }
    e.preventDefault();
    // Tên tham số là chữ THƯỜNG (conversationid): đó là cái ngăn kéo thực sự đọc (message_drawer.js, SHOW_CONVERSATION),
    // còn JSDoc của message_drawer_helper ghi `conversationId` là SAI. Truyền sai tên thì ngăn kéo mở trống và lõi ném
    // lỗi "is_contact(): null given".
    DrawerHelper.showConversation({conversationid: Number(link.dataset.conversationid)});
};

/**
 * Hỏi máy chủ một lần.
 *
 * @returns {Promise<Number>} Số giây chờ trước lần hỏi tiếp theo.
 */
const poll = async() => {
    const baseWait = intervalMs / 1000;
    try {
        // Tham số của Ajax.call: (requests, async, loginrequired, nosessionupdate). loginrequired PHẢI là true
        // (false là gọi service-nologin.php, không có phiên nên máy chủ từ chối). nosessionupdate = true để việc hỏi định kỳ
        // không kéo dài phiên đăng nhập: bỏ quên tab thì vẫn hết phiên đúng hạn.
        const result = await Ajax.call([{
            methodname: 'local_nttchat_get_updates',
            args: {lastid: lastId},
        }], true, true, true)[0];
        failures = 0;
        if (!result.available) {
            return UNAVAILABLE_WAIT;
        }
        const first = lastId === '';
        lastId = result.lastid;
        if (!first && result.events.length > 0) {
            await handleEvents(result.events);
            await updateUnreadBadge(result.unreadcount);
        }
        return baseWait;
    } catch (error) {
        failures++;
        Log.debug(`local_nttchat: hỏi tin mới lỗi (${failures}): ${error && error.message ? error.message : error}`);
        return Math.min(MAX_BACKOFF, baseWait * Math.pow(2, failures));
    }
};

/**
 * Lên lịch lần hỏi tiếp theo.
 *
 * @param {Number} seconds
 */
const schedule = (seconds) => {
    clearTimeout(timer);
    timer = setTimeout(tick, Math.max(1, seconds) * 1000);
};

/**
 * Một nhịp: hỏi nếu tab đang hiện, rồi hẹn nhịp sau.
 */
const tick = async() => {
    if (busy) {
        return;
    }
    if (document.hidden) {
        schedule(intervalMs / 1000);
        return;
    }
    busy = true;
    const wait = await poll();
    busy = false;
    schedule(wait);
};

/**
 * Khởi động. Gọi một lần trên mỗi trang (từ hook before_footer_html_generation).
 *
 * @param {Object} config
 * @param {Number} config.interval Chu kỳ hỏi (giây).
 */
export const init = ({interval}) => {
    if (started) {
        return;
    }
    started = true;
    intervalMs = Math.max(2, Number(interval) || 4) * 1000;
    document.addEventListener('click', onClick);
    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && !busy) {
            schedule(0); // Tab vừa hiện lại: hỏi ngay (tối thiểu 1 giây).
        }
    });
    schedule(0);
};
