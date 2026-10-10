const path = require('path');

let isPackaged = false;
try {
  isPackaged = require('node:sea').isSea();
} catch (e) {
  isPackaged = false;
}

const APP_ROOT = isPackaged
  ? path.dirname(process.execPath)
  : path.join(__dirname, '..');

module.exports = { APP_ROOT };
