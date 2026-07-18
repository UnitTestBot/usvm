"use strict";

const { magic } = require("./compiled/magic.js");

exports.invoke = ([x]) => magic(x);
