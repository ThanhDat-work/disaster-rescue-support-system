(() => {
  "use strict";

  function frame(command, headers) {
    if (!/^[A-Z]+$/.test(command)) throw new TypeError("Invalid STOMP command");
    const lines = [command];
    for (const [name, rawValue] of Object.entries(headers || {})) {
      const value = String(rawValue);
      if (!/^[A-Za-z0-9-]+$/.test(name) || /[\r\n\u0000]/.test(value)) {
        throw new TypeError("Invalid STOMP header");
      }
      lines.push(`${name}:${value}`);
    }
    // STOMP requires a blank line between headers and the terminating NUL.
    return `${lines.join("\n")}\n\n\u0000`;
  }

  function createParser() {
    let buffer = "";
    return {
      push(data) {
        buffer += String(data || "");
        if (buffer.length > 65536) {
          buffer = "";
          throw new Error("STOMP frame exceeds limit");
        }
        const result = [];
        let end;
        while ((end = buffer.indexOf("\u0000")) >= 0) {
          const raw = buffer.slice(0, end);
          buffer = buffer.slice(end + 1);
          const normalized = raw
            .replace(/^(?:\r?\n)+/, "")
            .replace(/\r\n/g, "\n");
          const separator = normalized.indexOf("\n\n");
          if (separator < 0) continue;
          const command = normalized.slice(0, normalized.indexOf("\n"));
          result.push({ command, body: normalized.slice(separator + 2) });
        }
        return result;
      },
    };
  }

  window.DrssStomp = Object.freeze({ frame, createParser });
})();
