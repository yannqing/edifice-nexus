import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

const load = createRequire(import.meta.url);
const { chromium } = load(process.env.PLAYWRIGHT_MODULE || "playwright");
const base = process.env.BASE_URL || "http://127.0.0.1:3310";
assert(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
const output = fs.mkdtempSync(path.join(os.tmpdir(), "edifice-contract-qa-"));
const user = { userId: "2111111111111111101", username: "contract-qa", realName: "合同测试员", status: 1, employmentStatus: 1 };
const roles = [{ roleId: "1", roleCode: "SUPER_ADMIN", roleName: "系统管理员" }];
const permissions = ["menu:my-projects", "menu:all-projects", "menu:workbench"];
const type = { projectTypeId: "1", projectTypeName: "全程结算服务", projectTypeCode: "A" };
const projectId = "2111111111111111111";
const creates = [], updates = [], errors = [], unexpected = [];
const sessions = new Map();
let project = null, holdUpload = null;

const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_EXECUTABLE || undefined });
const context = await browser.newContext({ viewport: { width: 1365, height: 960 } });
try {
  await context.addCookies([{ name: "access_token", value: "isolated-contract-qa", url: base }]);
  await context.addInitScript(({ user, roles, permissions }) => {
    localStorage.setItem("access_token", "isolated-contract-qa");
    localStorage.setItem("user_info", JSON.stringify(user));
    localStorage.setItem("user_roles", JSON.stringify(roles));
    localStorage.setItem("user_permissions", JSON.stringify(permissions));
  }, { user, roles, permissions });
  await context.route("**/*", async (route) => {
    const request = route.request(), url = new URL(request.url());
    if (url.origin !== base) return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const endpoint = url.pathname.slice(4);
    let data;
    if (endpoint === "/project/create") {
      const body = request.postDataJSON(); creates.push(body);
      project = { projectId, projectName: body.projectName, projectCode: "PJ-CONTRACT-QA", projectStatus: 0, archiveStatus: 0,
        projectType: type, projectStage: null, projectStages: [], fileCount: 0, preStartTime: null, preEndTime: null,
        projectMemberList: [{ projectMemberId: "1", projectId, userId: user.userId, projectRoleId: "101", realName: user.realName }],
        contract: { contractId: projectId, contractType: body.contractType, contractAmount: body.contractAmount,
          contractFile: null, contractOtherFiles: null, preStartDate: null, preEndDate: null } };
      data = projectId;
    } else if (endpoint === "/project/update") {
      const body = request.postDataJSON(); updates.push(body);
      if (body.projectName) project.projectName = body.projectName;
      if (body.contractFile) project.contract.contractFile = body.contractFile;
      if (body.contractOtherFiles?.length) {
        const existing = JSON.parse(project.contract.contractOtherFiles || "[]");
        project.contract.contractOtherFiles = JSON.stringify([...new Set([...existing, ...body.contractOtherFiles])]);
      }
      data = true;
    } else if (endpoint === `/project/details/${projectId}`) data = project;
    else if (endpoint === "/project/mine/tree") {
      data = { records: project ? [{ projectId, childCount: 0, selfMatches: true, project: { ...project, contractAmount: project.contract } }] : [],
        total: project ? 1 : 0, current: 1, size: 10, pages: project ? 1 : 0 };
    } else if (endpoint === "/project/mine/tree/statistics") {
      data = { allCount: project ? 1 : 0, notStartedCount: project ? 1 : 0, processingCount: 0, pendingAcceptanceCount: 0, completedCount: 0, archivedCount: 0,
        summary: { totalCount: project ? 1 : 0, childCount: 0, processingCount: 0, pendingAcceptanceCount: 0, completedCount: 0,
          rootContractAmount: project?.contract.contractAmount || 0, childContractAmount: 0 } };
    } else if (endpoint === "/file/uploads") {
      const body = request.postDataJSON(), id = "qa-" + (sessions.size + 1);
      const file = { fileId: String(2111111111111111200n + BigInt(sessions.size)), displayName: body.fileName, fileSize: String(body.totalSize),
        fileExtension: body.fileName.split(".").pop(), fileType: body.purpose, status: 1, fileUrl: "" };
      data = { uploadId: id, totalSize: body.totalSize, chunkSize: 5242880, totalParts: 1, status: "uploading", uploadedParts: [], partHashes: {}, file: null };
      sessions.set(id, { ...data, file });
    } else if (/^\/file\/uploads\/qa-\d+\/parts\/1$/.test(endpoint)) data = sessions.get(endpoint.split("/")[3]);
    else if (/^\/file\/uploads\/qa-\d+\/complete$/.test(endpoint)) {
      if (holdUpload) await holdUpload;
      data = { ...sessions.get(endpoint.split("/")[3]), status: "completed" };
    } else if (endpoint === "/auth/current-permissions") data = { roles, permissions };
    else if (endpoint === "/project/types") data = [type];
    else if (endpoint === "/users/candidates") data = { records: [user], total: 1, current: 1, size: 500, pages: 1 };
    else if (endpoint === "/project/stage-templates" || endpoint.endsWith("/my-pending")) data = [];
    else if (endpoint.includes("unread-count")) data = 0;
    else if (endpoint.includes("counts")) data = {};
    else { unexpected.push(endpoint); data = []; }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code: 200, data, msg: "success" }) });
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  const dialog = page.getByRole("dialog");
  const file = (name) => ({ name, mimeType: "application/octet-stream", buffer: Buffer.from("isolated contract fixture " + name) });
  const contractStep = () => dialog.getByRole("button", { name: /合同信息/ }).click();
  const memberStep = () => dialog.getByRole("button", { name: /成员管理|成员分配/ }).click();
  const edit = async () => {
    await page.getByRole("button", { name: "编辑" + project.projectName, exact: true }).click();
    await dialog.getByRole("button", { name: /合同信息/ }).waitFor();
    await contractStep();
  };

  await page.goto(base + "/my-projects");
  await page.getByRole("button", { name: "新建项目", exact: true }).click();
  await dialog.locator('input[type="text"]').first().fill("无需合同即可创建");
  await dialog.locator("select").first().selectOption("1");
  await contractStep();
  await dialog.getByText("合同文件（选填）", { exact: true }).waitFor();
  assert.equal(await dialog.locator('input[type="file"]').count(), 2);
  await dialog.getByPlaceholder("请输入合同金额", { exact: true }).fill("10");
  await memberStep();
  await dialog.getByRole("checkbox").first().check();
  await dialog.getByRole("button", { name: "创建项目", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal(creates.length, 1);
  assert.equal(creates[0].contractFile, undefined);
  assert.equal(creates[0].contractAmount, 100000);
  assert.deepEqual(creates[0].projectCharges, [user.userId]);
  assert.equal(sessions.size, 0, "Creating without a contract must not start uploads");

  await edit();
  let releaseUpload;
  holdUpload = new Promise((resolve) => { releaseUpload = resolve; });
  await dialog.locator('input[type="file"]').first().setInputFiles(file("补充合同.GBQ7"));
  await dialog.getByText("校验中", { exact: true }).waitFor();
  assert(await dialog.getByRole("button", { name: /成员管理/ }).isDisabled());
  assert(await dialog.getByRole("button", { name: "下一步", exact: true }).isDisabled());
  assert(await dialog.getByRole("button", { name: "取消", exact: true }).isDisabled());
  await page.keyboard.press("Escape");
  await dialog.waitFor({ state: "hidden" });
  await page.getByRole("region", { name: "上传任务", exact: true }).getByRole("button", { name: "打开编辑项目 · 无需合同即可创建", exact: true }).click();
  await dialog.waitFor();
  assert.equal(await dialog.getByText("校验中", { exact: true }).count(), 1, "Restoring a minimized form must preserve its running upload");
  releaseUpload(); holdUpload = null;
  await dialog.getByText("补充合同.GBQ7", { exact: true }).waitFor();
  await dialog.locator('input[type="file"]').first().setInputFiles(file("补充附件.pdf"));
  await dialog.getByText("补充附件.pdf", { exact: true }).waitFor();
  await memberStep();
  await dialog.getByRole("button", { name: "保存修改", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal(updates[0].contractFile, "2111111111111111200");
  assert.deepEqual(updates[0].contractOtherFiles, ["2111111111111111201"]);

  await edit();
  await dialog.getByText("已上传合同主文件1", { exact: true }).waitFor();
  await dialog.getByText("已上传合同附件1", { exact: true }).waitFor();
  await page.screenshot({ path: path.join(output, "edit-existing-contract.png") });
  // No file fields should be sent by ordinary edits, so existing references cannot be cleared.
  await memberStep();
  await dialog.getByRole("button", { name: "保存修改", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal(updates[1].contractFile, undefined);
  assert.equal(updates[1].contractOtherFiles, undefined);
  assert.equal(project.contract.contractFile, "2111111111111111200");
  assert.deepEqual(JSON.parse(project.contract.contractOtherFiles), ["2111111111111111201"]);

  await edit();
  await dialog.locator('input[type="file"]').first().setInputFiles(file("替换合同.pdf"));
  await dialog.getByText("替换合同.pdf", { exact: true }).waitFor();
  await dialog.getByText("已上传合同主文件1", { exact: true }).waitFor({ state: "hidden" });
  await dialog.getByRole("button", { name: "移除", exact: true }).click();
  await dialog.getByText("已上传合同主文件1", { exact: true }).waitFor();
  await dialog.locator('input[type="file"]').last().setInputFiles(file("第二批附件.GQI"));
  await dialog.getByText("第二批附件.GQI", { exact: true }).waitFor();
  await page.setViewportSize({ width: 375, height: 812 });
  await page.waitForFunction(() => document.querySelector('[role="dialog"]').getBoundingClientRect().width <= innerWidth, undefined, { timeout: 2000 });
  await page.screenshot({ path: path.join(output, "mobile-contract-edit.png"), fullPage: true });
  const overflow = await dialog.evaluate((element) => ({ client: element.clientWidth, scroll: element.scrollWidth }));
  assert(overflow.scroll <= overflow.client + 1, JSON.stringify(overflow));
  await memberStep();
  await dialog.getByRole("button", { name: "保存修改", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
  assert.equal(updates[2].contractFile, undefined, "Removing a newly selected replacement must preserve the saved main contract");
  assert.deepEqual(updates[2].contractOtherFiles, ["2111111111111111203"]);
  assert.deepEqual(JSON.parse(project.contract.contractOtherFiles), ["2111111111111111201", "2111111111111111203"]);
  assert.deepEqual(errors, []);
  assert.deepEqual(unexpected, []);
  console.log(JSON.stringify({ allApisMocked: true, createdWithoutContract: true, contractAddedLater: true, busyCloseBlocked: true,
    existingFilesPreserved: true, replacementCancelled: true, attachmentsAppended: true, largeIdsPreserved: true, mobileChecked: true, errors, output }));
} finally {
  await context.close();
  await browser.close();
}
