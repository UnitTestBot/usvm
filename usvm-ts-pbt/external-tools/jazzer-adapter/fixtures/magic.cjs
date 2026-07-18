"use strict";

function magic(x) {
  if (x * 2 === 98764) return 42;
  if (x > 0) return 1;
  return 0;
}

exports.invoke = ([x]) => magic(x);
