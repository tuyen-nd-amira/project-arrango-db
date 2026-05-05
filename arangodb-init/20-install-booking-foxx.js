'use strict';

const db = require('@arangodb').db;
const internal = require('internal');
const fm = require('@arangodb/foxx/manager');

const targetDatabase = internal.env.ARANGO_DATABASE || 'cinema_db';
const serviceMount = '/booking-tx';
const servicePath = '/docker-entrypoint-initdb.d/foxx-booking';

function ensureDatabase(name) {
  db._useDatabase('_system');
  if (name !== '_system' && !db._databases().includes(name)) {
    db._createDatabase(name);
    print('[arangodb-init] Created database: ' + name);
  }
  db._useDatabase(name);
}

function installService(mount, path) {
  try {
    fm.install(mount, path);
    return;
  } catch (error) {
    fm.install(path, mount);
  }
}

function reinstallService(mount, path) {
  try {
    fm.uninstall(mount, { force: true });
    print('[arangodb-init] Removed existing Foxx service at mount: ' + mount);
  } catch (error) {
    // Ignore when mount does not exist yet.
  }

  installService(mount, path);
  print('[arangodb-init] Installed Foxx service at mount: ' + mount);
}

ensureDatabase(targetDatabase);
reinstallService(serviceMount, servicePath);
