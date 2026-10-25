const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const sandbox = { window: {} };
const source = fs.readFileSync(
  path.join(__dirname, "../src/main/resources/static/js/stomp-protocol.js"),
  "utf8",
);
vm.runInNewContext(source, sandbox);
const { frame, createParser } = sandbox.window.DrssStomp;

const connect = frame("CONNECT", {
  "accept-version": "1.2",
  host: "localhost:8080",
  "X-CSRF-TOKEN": "test-only-token",
});
assert.equal(
  connect,
  "CONNECT\naccept-version:1.2\nhost:localhost:8080\n" +
    "X-CSRF-TOKEN:test-only-token\n\n\u0000",
);
assert.equal(
  frame("SUBSCRIBE", {
    id: "drss-1",
    destination: "/user/queue/notifications",
  }),
  "SUBSCRIBE\nid:drss-1\ndestination:/user/queue/notifications\n\n\u0000",
);
assert.throws(
  () => frame("CONNECT", { host: "bad\nheader" }),
  /Invalid STOMP header/,
);

const parser = createParser();
assert.equal(parser.push("\nCONNECTED\r\nversion:1.2\r\n\r\n").length, 0);
const connected = parser.push("\u0000");
assert.equal(connected[0].command, "CONNECTED");
const message = parser.push(
  "\nMESSAGE\ndestination:/user/queue/notifications\n\n" +
    '{"notificationId":"00000000-0000-0000-0000-000000000001"}\u0000',
);
assert.equal(message[0].command, "MESSAGE");
assert.equal(
  JSON.parse(message[0].body).notificationId,
  "00000000-0000-0000-0000-000000000001",
);
console.log("REALTIME_STOMP_CLIENT=PASS");
