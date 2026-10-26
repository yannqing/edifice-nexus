import assert from "node:assert/strict";
import { test } from "node:test";
import { UploadQueue } from "../src/lib/upload-queue.ts";

const tick = () => new Promise((resolve) => setImmediate(resolve));
const file = (name = "合同.pdf") => new File(["isolated queue test"], name);
const result = (id) => ({ fileId: String(id), displayName: "合同.pdf", fileSize: "10", fileExtension: "pdf", status: 1 });
function fixture(concurrency = 2) {
  const calls = [], cancelled = [];
  const queue = new UploadQueue((input, purpose, options) => new Promise((resolve, reject) => {
    calls.push({ input, purpose, options, resolve, reject });
    options.signal.addEventListener("abort", () => reject(new DOMException("Cancelled", "AbortError")), { once: true });
  }), async (id) => { cancelled.push(id); }, concurrency);
  const received = { A: [], B: [] }, busy = { A: [], B: [] };
  const bind = (id, owner = id, initial = []) => queue.bindGroup(id, { windowId: owner, scope: owner + ":" + id,
    purpose: "project", value: initial, onChange: (value) => { received[id] = value; }, onBusyChange: (value) => busy[id]?.push(value) });
  bind("A"); bind("B");
  return { queue, calls, cancelled, received, busy, bind };
}

test("global concurrency stays bounded across windows and background completions preserve all values", async () => {
  const f = fixture();
  try {
    f.queue.enqueue("A", [file("A1.pdf"), file("A2.pdf"), file("A3.pdf")]);
    f.queue.enqueue("B", [file("B1.pdf")]);
    assert.equal(f.calls.length, 2);
    f.calls[0].options.onProgress(48);
    assert.equal(f.queue.getSnapshot()[0].progress, 48);
    f.calls[0].resolve(result(1)); await tick();
    assert.equal(f.calls.length, 3);
    assert.equal(f.calls[2].input.name, "B1.pdf", "A large project batch must not starve a newly added project");
    f.calls[1].resolve(result(2)); await tick();
    assert.equal(f.calls.length, 4);
    f.calls[2].resolve(result(4)); f.calls[3].resolve(result(3)); await tick();
    assert.deepEqual(f.received.A.map((item) => item.fileId), ["1", "2", "3"]);
    assert.deepEqual(f.received.B.map((item) => item.fileId), ["4"]);
    assert(f.queue.getSnapshot().every((task) => task.status === "completed"));
    assert.equal(f.busy.A.at(-1), false); assert.equal(f.busy.B.at(-1), false);
  } finally { f.queue.dispose(); }
});

test("same-named uploads from different projects use isolated resume scopes", async () => {
  const f = fixture();
  try {
    const input = file();
    f.queue.enqueue("A", [input]); f.queue.enqueue("B", [input]);
    assert.notEqual(f.calls[0].options.scope, f.calls[1].options.scope);
    f.calls[1].resolve(result(2)); f.calls[0].resolve(result(1)); await tick();
    assert.deepEqual(f.received.A.map((item) => item.fileId), ["1"]);
    assert.deepEqual(f.received.B.map((item) => item.fileId), ["2"]);
  } finally { f.queue.dispose(); }
});

test("restoring a field replaces its callbacks without restarting or duplicating a transfer", async () => {
  const f = fixture();
  try {
    f.queue.enqueue("A", [file()]);
    let restored;
    f.queue.bindGroup("A", { windowId: "A", scope: "A:A", purpose: "project", value: [result(99)], onChange: (value) => { restored = value; } });
    f.calls[0].resolve(result(1)); await tick();
    assert.equal(f.calls.length, 1);
    assert.deepEqual(restored.map((item) => item.fileId), ["99", "1"]);
    assert.deepEqual(f.received.A, []);
  } finally { f.queue.dispose(); }
});

test("failed files remain busy until retried and successfully completed exactly once", async () => {
  const f = fixture();
  try {
    f.queue.enqueue("A", [file()]);
    f.calls[0].reject(new Error("Network failed")); await tick();
    const task = f.queue.getSnapshot()[0];
    assert.equal(task.status, "error"); assert.equal(f.busy.A.at(-1), true);
    f.queue.retry(task.id);
    assert.equal(f.calls.length, 2);
    f.calls[1].resolve(result(1)); await tick();
    assert.equal(f.queue.getSnapshot()[0].status, "completed");
    assert.equal(f.received.A.length, 1); assert.equal(f.busy.A.at(-1), false);
    f.queue.retry(task.id); assert.equal(f.calls.length, 2);
  } finally { f.queue.dispose(); }
});

test("cancelling waiting and active files never delivers cancelled results", async () => {
  const f = fixture(1);
  try {
    f.queue.enqueue("A", [file("first.pdf"), file("waiting.pdf")]);
    const [active, waiting] = f.queue.getSnapshot();
    f.queue.cancel(waiting.id); assert.equal(f.calls.length, 1);
    f.calls[0].options.onSession("session-A");
    f.queue.cancel(active.id); await tick();
    assert.equal(f.calls[0].options.signal.aborted, true);
    assert.deepEqual(f.cancelled, ["session-A"]);
    assert.deepEqual(f.received.A, []); assert.equal(f.busy.A.at(-1), false);
    assert(f.queue.getSnapshot().every((task) => task.status === "cancelled"));
  } finally { f.queue.dispose(); }
});

test("ending one project window does not interrupt another project's upload", async () => {
  const f = fixture();
  try {
    f.queue.enqueue("A", [file()]); f.queue.enqueue("B", [file()]);
    f.queue.removeWindow("A"); await tick();
    assert.equal(f.calls[0].options.signal.aborted, true);
    assert.equal(f.calls[1].options.signal.aborted, false);
    f.calls[1].resolve(result(2)); await tick();
    assert.deepEqual(f.received.A, []);
    assert.deepEqual(f.received.B.map((item) => item.fileId), ["2"]);
    assert(f.queue.getSnapshot().every((task) => task.windowId === "B"));
  } finally { f.queue.dispose(); }
});

test("logout/disposal aborts transfers and cannot deliver results into a new user's forms", async () => {
  const f = fixture();
  f.queue.enqueue("A", [file()]);
  f.calls[0].options.onSession("old-user-session");
  f.queue.dispose(); await tick();
  assert.equal(f.calls[0].options.signal.aborted, true);
  assert.deepEqual(f.queue.getSnapshot(), []);
  assert.deepEqual(f.received.A, []);
  assert.deepEqual(f.cancelled, [], "Do not send a deletion request with the next user's credentials");
  assert.throws(() => f.queue.enqueue("A", [file()]));
});

test("folder-relative paths are retained after background uploads", async () => {
  const f = fixture();
  try {
    const input = file("计算底稿.GBQ7");
    Object.defineProperty(input, "webkitRelativePath", { value: "资料/计算底稿.GBQ7" });
    f.queue.enqueue("A", [input]); f.calls[0].resolve(result(1)); await tick();
    assert.equal(f.received.A[0].relativePath, "资料/计算底稿.GBQ7");
    assert.equal(f.queue.getSnapshot()[0].name, "资料/计算底稿.GBQ7");
    f.queue.cancel(f.queue.getSnapshot()[0].id);
    assert.equal(f.queue.getSnapshot()[0].status, "completed");
  } finally { f.queue.dispose(); }
});

test("queue admission is atomic at the global pending-file limit", () => {
  const f = fixture();
  try {
    assert.throws(() => f.queue.enqueue("A", Array.from({ length: 501 }, () => file())), /500/);
    assert.equal(f.calls.length, 0); assert.deepEqual(f.queue.getSnapshot(), []);
  } finally { f.queue.dispose(); }
});
