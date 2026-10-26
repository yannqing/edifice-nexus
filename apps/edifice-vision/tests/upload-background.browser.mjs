import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || "playwright");
const base = process.env.BASE_URL || "http://127.0.0.1:3310";
assert(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
const output = fs.mkdtempSync(path.join(os.tmpdir(), "edifice-background-qa-"));
const user = { userId: "2111111111111111101", username: "background-qa", realName: "上传测试员", status: 1, employmentStatus: 1 };
const roles = [{ roleId: "1", roleCode: "SUPER_ADMIN", roleName: "系统管理员" }];
const permissions = ["menu:my-projects", "menu:all-projects", "menu:workbench"];
const projects = ["A", "B"].map((name, index) => {
  const projectId = String(2111111111111111111n + BigInt(index));
  return { projectId, projectName: "后台上传项目" + name, projectCode: "BACKGROUND-" + name,
    projectType: { projectTypeId: "1", projectTypeCode: "A", projectTypeName: "全程结算" }, projectStatus: 1, archiveStatus: 0,
    projectStage: null, projectStages: [], fileCount: 0, preStartTime: null, preEndTime: null,
    contractAmount: { contractId: projectId, contractType: 0, contractAmount: 100000, contractFile: null, contractOtherFiles: null },
    projectMemberList: [{ projectMemberId: projectId, projectId, userId: user.userId, realName: user.realName, projectRoleId: "101" }] };
});
const sessions = new Map(), holds = new Map(), drafts = [], saves = [], submissions = [], errors = [], unexpected = [];
const failureNames = new Set();
const paged = (records) => ({ records, total: records.length, current: 1, size: 10, pages: records.length ? 1 : 0 });
const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_EXECUTABLE || undefined });
const context = await browser.newContext({ viewport: { width: 1365, height: 960 } });
try {
  await context.addCookies([{ name: "access_token", value: "isolated-background-qa", url: base }]);
  await context.addInitScript(({ user, roles, permissions }) => {
    localStorage.setItem("access_token", "isolated-background-qa");
    localStorage.setItem("user_info", JSON.stringify(user)); localStorage.setItem("user_roles", JSON.stringify(roles));
    localStorage.setItem("user_permissions", JSON.stringify(permissions));
  }, { user, roles, permissions });
  await context.route("**/*", async (route) => {
    const req = route.request(), url = new URL(req.url());
    if (url.origin !== base) return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const p = url.pathname.slice(4);
    let data, code = 200, msg = "success";
    if (/^\/project\/(mine|all)\/tree$/.test(p)) data = paged(projects.map((project) => ({ projectId: project.projectId, childCount: 0, selfMatches: true, project })));
    else if (p.endsWith("/tree/statistics")) data = { allCount: 2, notStartedCount: 0, processingCount: 2, pendingAcceptanceCount: 0,
      completedCount: 0, archivedCount: 0, summary: { totalCount: 2, childCount: 0, processingCount: 2, pendingAcceptanceCount: 0,
        completedCount: 0, rootContractAmount: 200000, childContractAmount: 0 } };
    else if (p.startsWith("/project/details/")) {
      const project = projects.find((item) => item.projectId === p.split("/").at(-1));
      data = { ...project, contract: project.contractAmount };
    } else if (p === "/project-files/draft-policy") data = { retentionDays: 30, maxBatchFiles: 200 };
    else if (p === "/project-file-folders") data = [];
    else if (p === "/project-files/list") data = paged([]);
    else if (p === "/project-files/drafts" && req.method() === "GET") data = paged(drafts.filter((draft) => draft.projectId === url.searchParams.get("projectId")));
    else if (p === "/project-files/drafts" && req.method() === "POST") {
      const body = req.postDataJSON(); saves.push(body);
      data = body.map((item) => {
        const projectFileId = String(2111111111111111500n + BigInt(drafts.length));
        drafts.push({ ...item, projectFileId, createdTime: "2026-10-23T12:00:00", draftExpiresAt: "2026-11-22T12:00:00", approvalStatus: 0 });
        return projectFileId;
      });
    } else if (p === "/project-files/submit-drafts") { submissions.push(req.postDataJSON()); data = "application-1"; }
    else if (p === "/file/uploads" && req.method() === "POST") {
      const body = req.postDataJSON(), uploadId = "qa-" + (sessions.size + 1);
      const file = { fileId: String(2111111111111111200n + BigInt(sessions.size)), displayName: body.fileName, fileSize: String(body.totalSize),
        fileExtension: body.fileName.split(".").pop(), fileType: body.purpose, status: 1, fileUrl: "" };
      data = { uploadId, totalSize: body.totalSize, chunkSize: 5242880, totalParts: 1, status: "uploading", uploadedParts: [], partHashes: {}, file: null };
      sessions.set(uploadId, { ...data, file });
    } else if (/^\/file\/uploads\/qa-\d+\/parts\/1$/.test(p)) data = sessions.get(p.split("/")[3]);
    else if (/^\/file\/uploads\/qa-\d+\/complete$/.test(p)) {
      const session = sessions.get(p.split("/")[3]);
      const hold = holds.get(session.file.displayName);
      if (hold) await hold.promise;
      if (failureNames.has(session.file.displayName)) { code = 500; msg = "隔离测试：模拟校验失败"; }
      else data = { ...session, status: "completed" };
    } else if (/^\/file\/uploads\/qa-\d+$/.test(p) && req.method() === "DELETE") data = true;
    else if (/^\/file\/uploads\/qa-\d+$/.test(p) && req.method() === "GET") data = sessions.get(p.split("/")[3]);
    else if (p === "/auth/current-permissions") data = { roles, permissions };
    else if (p === "/auth/logout") data = true;
    else if (p === "/project/types") data = [projects[0].projectType];
    else if (p.includes("unread-count")) data = 0;
    else if (p.includes("counts")) data = {};
    else if (p.endsWith("/my-pending")) data = [];
    else { unexpected.push(p); data = []; }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code, data, msg }) }).catch(() => {});
  });
  const page = await context.newPage(); page.on("pageerror", (error) => errors.push(error.message));
  const panel = page.getByRole("region", { name: "上传任务", exact: true });
  const uploadDialog = page.getByRole("dialog", { name: "项目文件申请", exact: true });
  const inputFile = (name) => ({ name, mimeType: "application/octet-stream", buffer: Buffer.from("mocked background upload " + name) });
  const hold = (name) => { let resolve; const promise = new Promise((done) => { resolve = done; }); holds.set(name, { promise, resolve }); };
  const release = (name) => { holds.get(name)?.resolve(); holds.delete(name); };
  const openUpload = async (project) => {
    await page.getByRole("button", { name: `查看${project.projectName}详情`, exact: true }).click();
    await page.getByRole("dialog").getByRole("button", { name: "上传文件", exact: true }).click();
    await uploadDialog.getByRole("button", { name: "最小化上传窗口", exact: true }).waitFor();
    await uploadDialog.locator('input[type="file"]').first().waitFor({ state: "attached" });
    await page.waitForFunction(() => !document.querySelector('[role="dialog"][aria-labelledby] input[type="file"]:disabled'));
  };
  const minimize = async () => {
    await uploadDialog.getByRole("button", { name: "最小化上传窗口", exact: true }).click();
    await page.waitForFunction(() => document.querySelectorAll('[role="dialog"]').length === 0);
    await panel.waitFor();
  };
  const restore = async (project) => {
    await panel.getByRole("button", { name: `打开项目文件 · ${project.projectName}`, exact: true }).click();
    await uploadDialog.waitFor();
  };
  await page.goto(base + "/my-projects");
  hold("A计算底稿.GBQ7"); hold("B成果文件.pdf");
  await openUpload(projects[0]);
  await uploadDialog.getByText("草稿自保存起保留 30 天，到期失效；已提交文件不受影响。", { exact: true }).waitFor();
  await uploadDialog.locator('input[type="file"]').first().setInputFiles(inputFile("A计算底稿.GBQ7"));
  await uploadDialog.getByText("校验中", { exact: true }).waitFor();
  await minimize();
  await panel.getByRole("button", { name: "隐藏上传任务", exact: true }).click();
  await page.getByRole("button", { name: "显示上传任务", exact: true }).click();
  await openUpload(projects[1]);
  await uploadDialog.locator('input[type="file"]').first().setInputFiles(inputFile("B成果文件.pdf"));
  await uploadDialog.getByText("校验中", { exact: true }).waitFor();
  await minimize();
  assert.equal(sessions.size, 2);
  await page.getByRole("link", { name: "全部项目", exact: true }).click();
  await page.waitForURL("**/all-projects");
  await panel.getByText("A计算底稿.GBQ7", { exact: true }).waitFor();
  await panel.getByText("B成果文件.pdf", { exact: true }).waitFor();
  assert.equal(saves.length, 0); assert.equal(submissions.length, 0);
  release("B成果文件.pdf"); release("A计算底稿.GBQ7");
  await panel.getByText("文件已上传，待保存或提交", { exact: true }).first().waitFor();
  await page.screenshot({ path: path.join(output, "desktop-background-tasks.png") });
  await restore(projects[0]);
  await uploadDialog.getByText("A计算底稿.GBQ7", { exact: true }).waitFor();
  assert.equal(await uploadDialog.getByText("B成果文件.pdf", { exact: true }).count(), 0);
  await uploadDialog.getByRole("button", { name: "保存到草稿", exact: true }).click();
  await uploadDialog.getByRole("tab", { name: /我的草稿/, selected: true }).waitFor();
  await uploadDialog.getByText("A计算底稿.GBQ7", { exact: true }).waitFor();
  assert.equal(saves.length, 1); assert.equal(saves[0][0].projectId, projects[0].projectId);
  assert.equal(saves[0][0].fileId, "2111111111111111200");
  assert.equal(submissions.length, 0, "An upload or draft save must never submit approval automatically");
  await minimize();
  await restore(projects[1]);
  await uploadDialog.getByText("B成果文件.pdf", { exact: true }).waitFor();
  await uploadDialog.getByRole("button", { name: "保存到草稿", exact: true }).click();
  await uploadDialog.getByRole("tab", { name: /我的草稿/, selected: true }).waitFor();
  await uploadDialog.getByText("B成果文件.pdf", { exact: true }).waitFor();
  await uploadDialog.getByRole("tab", { name: "上传文件", exact: true }).click();
  failureNames.add("失败重试.GQI");
  await uploadDialog.locator('input[type="file"]').first().setInputFiles(inputFile("失败重试.GQI"));
  await uploadDialog.getByRole("alert").filter({ hasText: "隔离测试：模拟校验失败" }).waitFor();
  await minimize();
  failureNames.delete("失败重试.GQI");
  await panel.getByRole("button", { name: "重试上传失败重试.GQI", exact: true }).click();
  await panel.getByText("部分上传失败", { exact: true }).waitFor({ state: "hidden" });
  await restore(projects[1]);
  await uploadDialog.getByText("失败重试.GQI", { exact: true }).waitFor();
  await uploadDialog.getByRole("button", { name: "保存到草稿", exact: true }).click();
  await uploadDialog.getByRole("tab", { name: /我的草稿/, selected: true }).waitFor();
  await uploadDialog.getByRole("tab", { name: "上传文件", exact: true }).click();
  hold("待取消.pdf");
  await uploadDialog.locator('input[type="file"]').first().setInputFiles(inputFile("待取消.pdf"));
  await uploadDialog.getByText("校验中", { exact: true }).waitFor();
  await minimize();
  await panel.getByRole("button", { name: "取消上传待取消.pdf", exact: true }).click();
  release("待取消.pdf");
  await panel.getByText("已取消", { exact: true }).waitFor();
  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForFunction(() => document.querySelector("aside:not([aria-label])").getBoundingClientRect().right <= 1);
  await page.screenshot({ path: path.join(output, "mobile-background-tasks.png"), fullPage: true });
  const bounds = await panel.evaluate((element) => ({ x: element.getBoundingClientRect().x, right: element.getBoundingClientRect().right, width: innerWidth, scroll: element.scrollWidth, client: element.clientWidth }));
  assert(bounds.x >= 0 && bounds.right <= bounds.width && bounds.scroll <= bounds.client + 1, JSON.stringify(bounds));
  assert.equal(saves.length, 3);
  assert(saves.slice(1).every((batch) => batch.every((item) => item.projectId === projects[1].projectId)));
  assert.equal(submissions.length, 0);
  await restore(projects[1]);
  hold("退出时上传.pdf");
  await uploadDialog.locator('input[type="file"]').first().setInputFiles(inputFile("退出时上传.pdf"));
  await uploadDialog.getByText("校验中", { exact: true }).waitFor();
  await minimize();
  await page.setViewportSize({ width: 1365, height: 960 });
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.waitForURL("**/login");
  assert.equal(await page.getByRole("complementary", { name: "上传任务中心", exact: true }).count(), 0);
  release("退出时上传.pdf");
  assert.deepEqual(errors, []); assert.deepEqual(unexpected, []);
  console.log(JSON.stringify({ allApisMocked: true, multipleProjects: true, crossRoute: true, hideRestore: true, backgroundResultsRetained: true,
    correctDraftAssociations: true, retryCancel: true, logoutClearsTasks: true, noAutomaticApproval: true, mobileBounds: bounds, errors, output }));
} finally {
  for (const item of holds.values()) item.resolve();
  await context.close(); await browser.close();
}
