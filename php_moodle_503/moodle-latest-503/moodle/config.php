<?php // Moodle configuration file

unset($CFG);
global $CFG;
// Initialise $CFG as a new empty object to hold configuration settings
$CFG = new stdClass();


// Set the database type to MariaDB (compatible with MySQL)
$CFG->dbtype = 'mariadb';

// Set the database library driver to use native PHP extensions
$CFG->dblibrary = 'native';






// Define the hostname where the database server runs
$CFG->dbhost = '127.0.0.1';

// Define the name of the database that Moodle will use
$CFG->dbname = 'moodle_503';

// Define the username used to connect to the database
$CFG->dbuser = 'root';

// Define the password used to connect to the database
$CFG->dbpass = '1234';

// Set the table prefix for all Moodle tables in the database
$CFG->prefix = 'mdl_';

// Configure additional database options
$CFG->dboptions = array(
  // Do not keep a persistent connection open
  'dbpersist' => 0,
  // Port number for the database connection (default 3306)
  'dbport' => 3306,
  // Socket file for the database connection (empty means use TCP)
  'dbsocket' => '',
  // Character set and collation for the database
  'dbcollation' => 'utf8mb4_unicode_ci',
);




// Set the root URL of the Moodle site
$CFG->wwwroot = 'http://localhost:8000';

// Define the local filesystem root directory where Moodle stores data
$CFG->dataroot = '/var/www/moodledata';

// Set the default administrator username
$CFG->admin = 'admin';

// Set the permissions mode for Moodle directories (read/write/execute for owner, group, others)
$CFG->directorypermissions = 02777;

// Include the Moodle setup script which initialises the global $CFG and other settings
require_once(__DIR__ . '/lib/setup.php');



// There is no php closing tag in this file, it is intentional because it prevents trailing whitespace problems!
