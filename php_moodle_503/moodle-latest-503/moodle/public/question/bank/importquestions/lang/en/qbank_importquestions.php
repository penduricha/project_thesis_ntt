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
// along with Moodle.  If not, see <https://www.gnu.org/licenses/>.

/**
 * Strings for component 'qbank_importquestions', language 'en'
 *
 * @package   qbank_importquestions
 * @copyright 2021 Catalyst IT Australia Pty Ltd
 * @author    Marc-Alexandre Ghaly <marc-alexandreghaly@catalyst-ca.net>
 * @license   http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

$string['importfromotherserver'] = 'Import questions from other server';
$string['importfromotherserver_help'] = 'Download questions directly from another server, for example your own question bank backend, instead of uploading a file.

Fill in the connection and authentication settings, choose the data format that the server returns and the category to import into, then press **Import from server**.

Moodle\'s *HTTP security* settings (blocked hosts and allowed ports) also apply to this request. Only site administrators can use this feature.';
$string['importfromserverbutton'] = 'Import from server';
$string['invalidlimit'] = 'The number of questions must be between 0 and 1000 (0 means no limit).';
$string['invalidpath'] = 'The path must start with "/".';
$string['invalidport'] = 'The port must be a number between 1 and 65535.';
$string['invalidtimeout'] = 'The timeout must be between 1 and 120 seconds.';
$string['pluginname'] = 'Import questions';
$string['privacy:metadata'] = 'The Import questions question bank plugin imports questions from a file according to the selected file format.';
$string['serverauth'] = 'Authentication';
$string['serverauth_basic'] = 'Username and password';
$string['serverauth_none'] = 'None';
$string['serverauth_token'] = 'API key / Bearer token';
$string['serverauthentication'] = 'Authentication';
$string['serverbankid'] = 'Question bank ID';
$string['serverconnection'] = 'Server connection';
$string['serverdataformat'] = 'Data format';
$string['servererror'] = 'Could not retrieve the questions from the other server. {$a}';
$string['servererror_empty'] = 'The server returned an empty response.';
$string['servererror_http'] = 'The server responded with HTTP status {$a}.';
$string['serverfilter'] = 'Question filter';
$string['serverfilternotused'] = 'A direct file URL is downloaded as it is, so there is no filter to set.';
$string['serverhost'] = 'Server IP / hostname';
$string['serverlimit'] = 'Maximum number of questions';
$string['serverpassword'] = 'Password';
$string['serverpath'] = 'API endpoint / file path';
$string['serverport'] = 'Port';
$string['serverscheme'] = 'Protocol';
$string['servertimeout'] = 'Timeout (seconds)';
$string['servertoken'] = 'API key / token';
$string['servertopic'] = 'Topic / category';
$string['servertype'] = 'Server type';
$string['servertype_fileurl'] = 'Direct file URL';
$string['servertype_help'] = '**REST API**: Moodle sends a GET request to the endpoint with the query parameters *bank*, *topic*, *limit* and *format*, and expects the questions in the response body, written in the data format selected below.

**Direct file URL**: Moodle downloads the file found at the given path as it is, without any query parameter.';
$string['servertype_rest'] = 'REST API';
$string['serverusername'] = 'Username';
$string['serververifyssl'] = 'Verify SSL certificate';