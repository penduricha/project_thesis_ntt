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
 * Defines the import questions form.
 *
 * @package    qbank_importquestions
 * @copyright  1999 onwards Martin Dougiamas  {@link http://moodle.com}
 * @license    http://www.gnu.org/copyleft/gpl.html GNU GPL v3 or later
 */


require_once(__DIR__ . '/../../../config.php');
require_once($CFG->dirroot . '/question/editlib.php');
require_once($CFG->dirroot . '/question/format.php');
require_once($CFG->dirroot . '/question/renderer.php');
require_once($CFG->libdir . '/filelib.php');

use qbank_importquestions\form\question_import_form;
use qbank_importquestions\form\server_import_form;

/**
 * Download the questions from the other server into a local file.
 *
 * Uses Moodle's curl class, so the "HTTP security" settings (blocked hosts / allowed ports) still apply.
 *
 * @param stdClass $form the submitted data of the server import form.
 * @param string $targetfile the local file to write to.
 * @param moodle_url $returnurl where the "Continue" link of an error page should go.
 * @throws moodle_exception if the server cannot be reached or returns nothing usable.
 */
function qbank_importquestions_fetch_from_server(stdClass $form, string $targetfile, moodle_url $returnurl): void
{
    $component = 'qbank_importquestions';

    $scheme = ($form->serverscheme ?? 'http') === 'https' ? 'https' : 'http';
    $url = new moodle_url("{$scheme}://{$form->serverhost}:" . (int) $form->serverport . $form->serverpath);

    // Query parameters are only meaningful for a REST API; a direct file URL is downloaded as is.
    if ($form->servertype === 'rest') {
        $params = ['format' => $form->serverformat];
        if (!empty($form->serverbankid)) {
            $params['bank'] = $form->serverbankid;
        }
        if (!empty($form->servertopic)) {
            $params['topic'] = $form->servertopic;
        }
        if (!empty($form->serverlimit)) {
            $params['limit'] = (int) $form->serverlimit;
        }
        $url->params($params);
    }

    $headers = [];
    $options = [
        'CURLOPT_TIMEOUT' => max(1, (int) $form->servertimeout),
        'CURLOPT_CONNECTTIMEOUT' => max(1, (int) $form->servertimeout),
    ];

    if ($form->serverauth === 'token') {
        // Send the token both ways, so the backend can use whichever it prefers.
        $headers[] = 'Authorization: Bearer ' . $form->servertoken;
        $headers[] = 'X-API-Key: ' . $form->servertoken;
    } else if ($form->serverauth === 'basic') {
        $options['CURLOPT_HTTPAUTH'] = CURLAUTH_BASIC;
        $options['CURLOPT_USERPWD'] = ($form->serverusername ?? '') . ':' . ($form->serverpassword ?? '');
    }

    if ($scheme === 'https' && empty($form->serververifyssl)) {
        $options['CURLOPT_SSL_VERIFYPEER'] = false;
        $options['CURLOPT_SSL_VERIFYHOST'] = 0;
    }

    $curl = new curl();
    $curl->setHeader($headers);
    $content = $curl->get($url->out(false), [], $options);

    $httpcode = (int) ($curl->get_info()['http_code'] ?? 0);
    if ($curl->get_errno() || $content === false) {
        // Includes the case where Moodle's own cURL security helper blocked the host or the port.
        throw new moodle_exception('servererror', $component, $returnurl->out(), $curl->error);
    }
    if ($httpcode !== 200) {
        throw new moodle_exception(
            'servererror',
            $component,
            $returnurl->out(),
            get_string('servererror_http', $component, $httpcode)
        );
    }
    if (trim((string) $content) === '') {
        throw new moodle_exception(
            'servererror',
            $component,
            $returnurl->out(),
            get_string('servererror_empty', $component)
        );
    }
    if (file_put_contents($targetfile, $content) === false) {
        throw new moodle_exception('uploadproblem');
    }
}

require_login();
core_question\local\bank\helper::require_plugin_enabled('qbank_importquestions');
list($thispageurl, $contexts, $cmid, $cm, $module, $pagevars) =
    question_edit_setup('import', '/question/bank/importquestions/import.php');

// Get display strings.
$txt = new stdClass();
$txt->importerror = get_string('importerror', 'question');
$txt->importquestions = get_string('importquestions', 'question');

list($catid, $catcontext) = explode(',', $pagevars['cat']);
if (!$category = $DB->get_record("question_categories", ['id' => $catid])) {
    throw new moodle_exception('nocategory', 'question');
}

$categorycontext = context::instance_by_id($category->contextid);
$category->context = $categorycontext;
// This page can be called without cmid in which case.
// We get the context from the category object.
if ($contexts === null) { // Need to get the course from the chosen category.
    $contexts = new core_question\local\bank\question_edit_contexts($categorycontext);
    $thiscontext = $contexts->lowest();
    [$module, $cm] = get_module_from_cmid($thiscontext->instanceid);
    require_login($cm->course, false, $cm);
    $contexts->require_one_edit_tab_cap($edittab);
}

$PAGE->set_url($thispageurl);

$formcustomdata = [
    'contexts' => $contexts->having_one_edit_tab_cap('import'),
    'defaultcategory' => $pagevars['cat'],
];

// Business 1: import questions from a file.
$importform = new question_import_form($thispageurl, $formcustomdata);

// Business 2: import questions from another server.
// Moodle makes a request to a host chosen by the user, so this is limited to site administrators.
$serverform = is_siteadmin() ? new server_import_form($thispageurl, $formcustomdata) : null;

if ($importform->is_cancelled()) {
    redirect($thispageurl);
}
// Page header.
$PAGE->set_title($txt->importquestions);
$PAGE->set_heading($COURSE->fullname);
$PAGE->activityheader->disable();
$PAGE->set_show_navigation_footer(false);

echo $OUTPUT->header();

// Print horizontal nav if needed.
$renderer = $PAGE->get_renderer('core_question', 'bank');

$qbankaction = new \core_question\output\qbank_action_menu($thispageurl);
echo $renderer->render($qbankaction);

// Work out which of the two forms was submitted. Both end up in the same $import object,
// so the real import below is shared.
$import = null;

if ($form = $importform->get_data()) {
    // Work out if this is an uploaded file.
    // Or one from the filesarea.
    $realfilename = $importform->get_new_filename('newfile');
    $importfile = make_request_directory() . "/{$realfilename}";
    if (!$result = $importform->save_file('newfile', $importfile, true)) {
        throw new moodle_exception('uploadproblem');
    }

    $import = (object) [
        'format' => $form->format,
        'category' => $category,
        'importfile' => $importfile,
        'realfilename' => $realfilename,
        'matchgrades' => $form->matchgrades,
        'catfromfile' => !empty($form->catfromfile),
        'contextfromfile' => !empty($form->contextfromfile),
        'stoponerror' => $form->stoponerror,
    ];
} else if ($serverform && ($serverdata = $serverform->get_data())) {
    if (!is_siteadmin()) {
        throw new moodle_exception(
            'nopermissions',
            'error',
            $thispageurl->out(),
            get_string('importfromotherserver', 'qbank_importquestions')
        );
    }

    // The category chosen in the server form is "categoryid,contextid".
    [$servercatid] = explode(',', $serverdata->servercategory);
    $servercategory = $DB->get_record('question_categories', ['id' => (int) $servercatid], '*', MUST_EXIST);
    $servercategory->context = context::instance_by_id($servercategory->contextid);

    $realfilename = 'server_import.' . ($serverdata->serverformat === 'xml' ? 'xml' : 'txt');
    $importfile = make_request_directory() . "/{$realfilename}";
    qbank_importquestions_fetch_from_server($serverdata, $importfile, $thispageurl);

    $import = (object) [
        'format' => $serverdata->serverformat,
        'category' => $servercategory,
        'importfile' => $importfile,
        'realfilename' => $realfilename,
        'matchgrades' => $serverdata->servermatchgrades,
        'catfromfile' => !empty($serverdata->servercatfromfile),
        'contextfromfile' => !empty($serverdata->servercontextfromfile),
        'stoponerror' => $serverdata->serverstoponerror,
    ];
}

// A form was submitted and the data is ready to be imported.
if ($import) {

    $formatfile = $CFG->dirroot . '/question/format/' . $import->format . '/format.php';
    if (!is_readable($formatfile)) {
        throw new moodle_exception('formatnotfound', 'question', '', $import->format);
    }

    require_once($formatfile);

    $classname = 'qformat_' . $import->format;
    $qformat = new $classname();

    // Load data into class.
    $qformat->setCategory($import->category);
    $qformat->setContexts($contexts->having_one_edit_tab_cap('import'));
    $qformat->setCourse($COURSE);
    $qformat->setFilename($import->importfile);
    $qformat->setRealfilename($import->realfilename);
    $qformat->setMatchgrades($import->matchgrades);
    $qformat->setCatfromfile($import->catfromfile);
    $qformat->setContextfromfile($import->contextfromfile);
    $qformat->setStoponerror($import->stoponerror);

    // Do anything before that we need to.
    if (!$qformat->importpreprocess()) {
        throw new moodle_exception('cannotimport', '', $thispageurl->out());
    }

    // Process the uploaded file.
    if (!$qformat->importprocess()) {
        throw new moodle_exception('cannotimport', '', $thispageurl->out());
    }

    // In case anything needs to be done after.
    if (!$qformat->importpostprocess()) {
        throw new moodle_exception('cannotimport', '', $thispageurl->out());
    }

    // Log the import into this category.
    $eventparams = [
        'contextid' => $qformat->category->contextid,
        'other' => ['format' => $import->format, 'categoryid' => $qformat->category->id],
    ];
    $event = \core\event\questions_imported::create($eventparams);
    $event->trigger();

    $params = $thispageurl->params() + ['category' => $qformat->category->id . ',' . $qformat->category->contextid];
    echo $OUTPUT->continue_button(new moodle_url('/question/edit.php', $params));
    echo $OUTPUT->footer();
    exit;
}

// Business 1: title + form (unchanged).
echo $OUTPUT->heading_with_help($txt->importquestions, 'importquestions', 'question');
$importform->display();

// Business 2: its own title + its own form, with its own "Expand all" link and sub-sections.
if ($serverform) {
    echo html_writer::start_div('mt-5');
    echo $OUTPUT->heading_with_help(
        get_string('importfromotherserver', 'qbank_importquestions'),
        'importfromotherserver',
        'qbank_importquestions'
    );
    $serverform->display();
    echo html_writer::end_div();
}

echo $OUTPUT->footer();