const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.join(__dirname, "../src/main/resources");
const vendor = path.join(root, "static/js/vendor/stompjs-7.2.0.min.js");
const app = fs.readFileSync(path.join(root, "static/js/main.js"), "utf8");
const layout = fs.readFileSync(
  path.join(root, "templates/layouts/main-layout.html"),
  "utf8",
);

assert.ok(fs.statSync(vendor).size > 10000, "Missing STOMP.js UMD bundle");
assert.match(layout, /\/js\/vendor\/stompjs-7\.2\.0\.min\.js/);
assert.match(app, /new window\.StompJs\.Client/);
assert.match(app, /heartbeatIncoming:\s*10000/);
assert.match(app, /heartbeatOutgoing:\s*10000/);
assert.match(app, /discardWebsocketOnCommFailure:\s*true/);
assert.match(app, /connectHeaders:\s*csrf/);
assert.doesNotMatch(app, /DrssStomp|heart-beat:\s*["']0,0/);
console.log("REALTIME_STOMP_CLIENT=PASS");
