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
 * English strings for local_nttchat.
 *
 * @package    local_nttchat
 * @copyright  2026 NTTU
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

defined('MOODLE_INTERNAL') || die();

$string['pluginname'] = 'NTT chat groups';
$string['nttchatgroups'] = 'Chat groups';
$string['nttchat:creategroup'] = 'Create and manage own chat groups';

// Page.
$string['pagetitle'] = 'Chat groups';
$string['pageintro'] = 'Create groups, add or remove members and chat in the messaging drawer. Group messages are regular Moodle messages.';
$string['creategroup'] = 'Create group';
$string['newgroup'] = 'New chat group';
$string['groupname'] = 'Group name';
$string['groupnameplaceholder'] = 'For example: TOEIC Part 5 study group';
$string['searchmembers'] = 'Add members';
$string['searchplaceholder'] = 'Type at least 2 characters of a name';
$string['nosearchresults'] = 'No one found.';
$string['selectedmembers'] = 'Selected members';
$string['nomembersselected'] = 'No members selected yet.';
$string['yourgroups'] = 'Your groups';
$string['nogroups'] = 'You are not in any chat group yet.';
$string['membercount'] = '{$a} members';
$string['owner'] = 'Owner';
$string['openchat'] = 'Open chat';
$string['manage'] = 'Manage';
$string['closemanage'] = 'Close';
$string['save'] = 'Save';
$string['cancel'] = 'Cancel';
$string['add'] = 'Add';
$string['remove'] = 'Remove';
$string['members'] = 'Members';
$string['leavegroup'] = 'Leave group';
$string['leaveconfirm'] = 'Leave this group? You will stop receiving its messages.';
$string['deletegroup'] = 'Delete group';
$string['deleteconfirm'] = 'Delete this group and all of its messages for everyone? This cannot be undone.';
$string['removeconfirm'] = 'Remove {$a} from this group?';
$string['directmessage'] = 'Direct message';
$string['directmessagehelp'] = 'Search for someone and start a private chat.';
$string['sendmessage'] = 'Send message';
$string['messagingdisabled'] = 'Messaging is disabled on this site.';

// Errors.
$string['invalidname'] = 'The group name must be between 1 and {$a} characters.';
$string['invaliduser'] = 'User {$a} cannot be added to a group.';
$string['cannotaddmember'] = '{$a} does not accept messages from you, so they cannot be added.';
$string['needmembers'] = 'Choose at least one other member.';
$string['toomanymembers'] = 'A group can have at most {$a} members.';
$string['toomanygroups'] = 'You can own at most {$a} groups.';
$string['groupnotfound'] = 'Group not found.';
$string['onlyowner'] = 'Only the group owner can do this.';
$string['cannotremoveowner'] = 'The owner cannot be removed. Leave the group or delete it instead.';
$string['notmember'] = 'This person is not in the group.';

// Real-time notifications.
$string['newmessagefrom'] = 'New message from {$a}';
$string['newgroupmessage'] = '{$a->from} in {$a->group}';

// Settings.
$string['settingsrealtime'] = 'Real-time new message alerts';
$string['settingsrealtime_desc'] = 'Show a notice and refresh the unread counter when a new message arrives. Needs Redis (see README).';
$string['settingspollinterval'] = 'Check interval (seconds)';
$string['settingspollinterval_desc'] = 'How often an open tab asks for new messages. Each check costs one Redis command when there is nothing new. Between 2 and 60.';
$string['settingsmaxgroupmembers'] = 'Maximum members per group';
$string['settingsmaxgroupsperuser'] = 'Maximum groups one person can own';
$string['redisstatus'] = 'Redis connection';
$string['redisstatus_ok'] = 'Connected.';
$string['redisstatus_notconfigured'] = 'Not configured. Add $CFG->local_nttchat_redis to config.php (see the README of this plugin).';
$string['redisstatus_noext'] = 'The PHP "redis" extension is not installed.';
$string['redisstatus_failed'] = 'Configured but the server is not reachable. Chat still works; alerts are delayed to the core refresh.';

// Privacy.
$string['privacy:metadata'] = 'The plugin stores no personal data of its own. Group chats are kept by the core messaging system. Short-lived new-message alerts (sender, a preview of up to 120 characters) are held in Redis for at most one day.';
