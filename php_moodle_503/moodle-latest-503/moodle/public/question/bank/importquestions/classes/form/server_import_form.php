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
 * Defines the "Import questions from other server" form.
 *
 * This is a separate form (a separate business flow) from {@see question_import_form}.
 * It is displayed under its own page title, with its own "Expand all" link and its own sub-sections.
 * Every element name starts with "server" so it never collides with the ids of the file import form.
 *
 * @package    qbank_importquestions
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */

namespace qbank_importquestions\form;

defined('MOODLE_INTERNAL') || die();

use moodleform;

require_once($CFG->libdir . '/formslib.php');

/**
 * Form to import questions from another server into the question bank.
 *
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */
class server_import_form extends moodleform
{

    /**
     * Build the form definition.
     */
    protected function definition()
    {
        global $OUTPUT;

        $mform = $this->_form;
        $component = 'qbank_importquestions';

        $defaultcategory = $this->_customdata['defaultcategory'];
        $contexts = $this->_customdata['contexts'];

        // ---------------------------------------------------------------------------------------------
        // Sub-section 1: Server connection.
        // ---------------------------------------------------------------------------------------------
        $mform->addElement('header', 'serverconnection', get_string('serverconnection', $component));
        $mform->setExpanded('serverconnection', true);

        $mform->addElement('select', 'servertype', get_string('servertype', $component), [
            'rest' => get_string('servertype_rest', $component),
            'fileurl' => get_string('servertype_fileurl', $component),
        ]);
        $mform->addHelpButton('servertype', 'servertype', $component);

        $mform->addElement('select', 'serverscheme', get_string('serverscheme', $component), [
            'http' => 'HTTP',
            'https' => 'HTTPS',
        ]);

        $mform->addElement(
            'text',
            'serverhost',
            get_string('serverhost', $component),
            ['size' => 40, 'placeholder' => '192.168.1.50']
        );
        $mform->setType('serverhost', PARAM_HOST);

        $mform->addElement('text', 'serverport', get_string('serverport', $component), ['size' => 6]);
        $mform->setType('serverport', PARAM_INT);
        $mform->setDefault('serverport', 8000);

        $mform->addElement('text', 'serverpath', get_string('serverpath', $component), ['size' => 40]);
        $mform->setType('serverpath', PARAM_RAW_TRIMMED);
        $mform->setDefault('serverpath', '/api/v1/questions');

        $mform->addElement('text', 'servertimeout', get_string('servertimeout', $component), ['size' => 4]);
        $mform->setType('servertimeout', PARAM_INT);
        $mform->setDefault('servertimeout', 15);

        $mform->addElement('advcheckbox', 'serververifyssl', get_string('serververifyssl', $component));
        $mform->setDefault('serververifyssl', 1);
        $mform->hideIf('serververifyssl', 'serverscheme', 'neq', 'https');

        // ---------------------------------------------------------------------------------------------
        // Sub-section 2: Authentication.
        // ---------------------------------------------------------------------------------------------
        $mform->addElement('header', 'serverauthentication', get_string('serverauthentication', $component));

        $mform->addElement('select', 'serverauth', get_string('serverauth', $component), [
            'none' => get_string('serverauth_none', $component),
            'token' => get_string('serverauth_token', $component),
            'basic' => get_string('serverauth_basic', $component),
        ]);

        $mform->addElement('passwordunmask', 'servertoken', get_string('servertoken', $component), ['size' => 40]);
        $mform->setType('servertoken', PARAM_RAW_TRIMMED);
        $mform->hideIf('servertoken', 'serverauth', 'neq', 'token');

        $mform->addElement('text', 'serverusername', get_string('serverusername', $component), ['size' => 30]);
        $mform->setType('serverusername', PARAM_RAW_TRIMMED);
        $mform->hideIf('serverusername', 'serverauth', 'neq', 'basic');

        $mform->addElement('passwordunmask', 'serverpassword', get_string('serverpassword', $component), ['size' => 30]);
        $mform->setType('serverpassword', PARAM_RAW);
        $mform->hideIf('serverpassword', 'serverauth', 'neq', 'basic');

        // ---------------------------------------------------------------------------------------------
        // Sub-section 3: Question filter (REST API only).
        // ---------------------------------------------------------------------------------------------
        $mform->addElement('header', 'serverfilter', get_string('serverfilter', $component));

        $mform->addElement(
            'text',
            'serverbankid',
            get_string('serverbankid', $component),
            ['size' => 30, 'placeholder' => '']
        );
        $mform->setType('serverbankid', PARAM_RAW_TRIMMED);

        $mform->addElement('text', 'servertopic', get_string('servertopic', $component), ['size' => 30]);
        $mform->setType('servertopic', PARAM_RAW_TRIMMED);

        $mform->addElement('text', 'serverlimit', get_string('serverlimit', $component), ['size' => 6]);
        $mform->setType('serverlimit', PARAM_INT);
        $mform->setDefault('serverlimit', 50);

        foreach (['serverbankid', 'servertopic', 'serverlimit'] as $field) {
            $mform->hideIf($field, 'servertype', 'eq', 'fileurl');
        }

        // A direct file URL has no filter, so say it instead of leaving the section empty.
        $mform->addElement('static', 'serverfilternote', '', get_string('serverfilternotused', $component));
        $mform->hideIf('serverfilternote', 'servertype', 'neq', 'fileurl');

        // ---------------------------------------------------------------------------------------------
        // Sub-section 4: Data format returned by the server (how Moodle parses the response).
        // ---------------------------------------------------------------------------------------------
        $mform->addElement('header', 'serverdataformat', get_string('serverdataformat', $component));

        $formatnames = get_import_export_formats('import');
        $radioarray = [];
        $separators = [];
        foreach ($formatnames as $shortname => $formatname) {
            $radioarray[] = $mform->createElement('radio', 'serverformat', '', $formatname, $shortname);

            $separator = '';
            if (get_string_manager()->string_exists('pluginname_help', 'qformat_' . $shortname)) {
                $separator .= $OUTPUT->help_icon('pluginname', 'qformat_' . $shortname);
            }
            $separator .= '<div class="w-100"></div>';
            $separators[] = $separator;
        }

        $radioarray[] = $mform->createElement('static', 'servermakelasthelpiconshowup', '');
        $mform->addGroup($radioarray, 'serverformatchoices', '', $separators, false);
        $mform->addRule('serverformatchoices', null, 'required', null, 'client');
        if (isset($formatnames['gift'])) {
            $mform->setDefault('serverformat', 'gift');
        }

        // ---------------------------------------------------------------------------------------------
        // Sub-section 5: General (where the questions are imported to).
        // ---------------------------------------------------------------------------------------------
        $mform->addElement('header', 'servergeneral', get_string('general', 'form'));

        $mform->addElement(
            'questioncategory',
            'servercategory',
            get_string('importcategory', 'question'),
            compact('contexts')
        );
        $mform->setDefault('servercategory', $defaultcategory);
        $mform->addHelpButton('servercategory', 'importcategory', 'question');

        $categorygroup = [];
        $categorygroup[] = $mform->createElement(
            'checkbox',
            'servercatfromfile',
            '',
            get_string('getcategoryfromfile', 'question')
        );
        $categorygroup[] = $mform->createElement(
            'checkbox',
            'servercontextfromfile',
            '',
            get_string('getcontextfromfile', 'question')
        );
        $mform->addGroup($categorygroup, 'servercategorygroup', '', '', false);
        $mform->disabledIf('servercontextfromfile', 'servercatfromfile', 'notchecked');
        $mform->setDefault('servercatfromfile', 1);
        $mform->setDefault('servercontextfromfile', 1);

        $matchgrades = [];
        $matchgrades['error'] = get_string('matchgradeserror', 'question');
        $matchgrades['nearest'] = get_string('matchgradesnearest', 'question');
        $mform->addElement('select', 'servermatchgrades', get_string('matchgrades', 'question'), $matchgrades);
        $mform->addHelpButton('servermatchgrades', 'matchgrades', 'question');
        $mform->setDefault('servermatchgrades', 'error');

        $mform->addElement('selectyesno', 'serverstoponerror', get_string('stoponerror', 'question'));
        $mform->setDefault('serverstoponerror', 1);
        $mform->addHelpButton('serverstoponerror', 'stoponerror', 'question');

        // ---------------------------------------------------------------------------------------------
        // Submit button, outside the collapsible sub-sections (same way add_action_buttons() does it).
        // ---------------------------------------------------------------------------------------------
        $buttonarray = [];
        $buttonarray[] = $mform->createElement('submit', 'serversubmit', get_string('importfromserverbutton', $component));
        $mform->addGroup($buttonarray, 'serverbuttonar', '', [' '], false);
        $mform->closeHeaderBefore('serverbuttonar');
    }

    /**
     * Validation of the connection settings.
     *
     * Fields hidden by hideIf() are not submitted, so every key is read defensively.
     *
     * @param array $data
     * @param array $files
     * @return array the errors that were found
     */
    public function validation($data, $files)
    {
        $errors = parent::validation($data, $files);
        $component = 'qbank_importquestions';

        // The format tells Moodle how to parse what the server returns.
        $formats = get_import_export_formats('import');
        if (empty($data['serverformat']) || !array_key_exists($data['serverformat'], $formats)) {
            $errors['serverformatchoices'] = get_string('required');
        }

        if (trim($data['serverhost'] ?? '') === '') {
            $errors['serverhost'] = get_string('required');
        }

        $port = (int) ($data['serverport'] ?? 0);
        if ($port < 1 || $port > 65535) {
            $errors['serverport'] = get_string('invalidport', $component);
        }

        $path = trim($data['serverpath'] ?? '');
        if ($path === '' || $path[0] !== '/') {
            $errors['serverpath'] = get_string('invalidpath', $component);
        }

        $timeout = (int) ($data['servertimeout'] ?? 0);
        if ($timeout < 1 || $timeout > 120) {
            $errors['servertimeout'] = get_string('invalidtimeout', $component);
        }

        $auth = $data['serverauth'] ?? 'none';
        if ($auth === 'token' && trim($data['servertoken'] ?? '') === '') {
            $errors['servertoken'] = get_string('required');
        }
        if ($auth === 'basic' && trim($data['serverusername'] ?? '') === '') {
            $errors['serverusername'] = get_string('required');
        }

        if (($data['servertype'] ?? 'rest') === 'rest') {
            $limit = (int) ($data['serverlimit'] ?? 0);
            if ($limit < 0 || $limit > 1000) {
                $errors['serverlimit'] = get_string('invalidlimit', $component);
            }
        }

        return $errors;
    }
}