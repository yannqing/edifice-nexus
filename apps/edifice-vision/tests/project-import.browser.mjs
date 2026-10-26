import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { createRequire } from "node:module";

const load = createRequire(import.meta.url);
const { chromium } = load(process.env.PLAYWRIGHT_MODULE || "playwright");
const base = process.env.BASE_URL || "http://127.0.0.1:3310";
assert(["127.0.0.1", "localhost"].includes(new URL(base).hostname));
const output = fs.mkdtempSync(path.join(os.tmpdir(), "edifice-import-qa-"));
const user = { userId: "2111111111111111101", username: "import-qa", realName: "导入测试员", status: 1, employmentStatus: 1 };
const roles = [{ roleId: "1", roleCode: "SUPER_ADMIN", roleName: "系统管理员" }];
const permissions = ["menu:my-projects", "menu:all-projects", "menu:workbench"];
const sessions = new Map();
const imports = [], errors = [], unexpected = [];
let holdUpload = null, holdImport = null, failNextImport = false;
const zeroSummary = { totalCount: 0, childCount: 0, processingCount: 0, pendingAcceptanceCount: 0, completedCount: 0, rootContractAmount: 0, childContractAmount: 0 };

const browser = await chromium.launch({ headless: true, executablePath: process.env.CHROME_EXECUTABLE || undefined });
const context = await browser.newContext({ viewport: { width: 1365, height: 960 } });
try {
  await context.addCookies([{ name: "access_token", value: "isolated-import-qa", url: base }]);
  await context.addInitScript(({ user, roles, permissions }) => {
    localStorage.setItem("access_token", "isolated-import-qa");
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
    if (endpoint === "/file/uploads" && request.method() === "POST") {
      const body = request.postDataJSON(), id = "qa-" + (sessions.size + 1);
      const file = { fileId: String(1000 + sessions.size), displayName: body.fileName, fileSize: String(body.totalSize),
        fileExtension: body.fileName.split(".").pop(), fileType: body.purpose, status: 1, fileUrl: "" };
      data = { uploadId: id, totalSize: body.totalSize, chunkSize: 8388608, totalParts: 1, status: "uploading", uploadedParts: [], partHashes: {}, file: null };
      sessions.set(id, { ...data, file });
    } else if (/^\/file\/uploads\/qa-\d+\/parts\/1$/.test(endpoint)) {
      data = sessions.get(endpoint.split("/")[3]);
    } else if (/^\/file\/uploads\/qa-\d+\/complete$/.test(endpoint)) {
      if (holdUpload) await holdUpload;
      data = { ...sessions.get(endpoint.split("/")[3]), status: "completed" };
    } else if (endpoint === "/project/import-file" && request.method() === "POST") {
      imports.push(request.postDataJSON());
      if (holdImport) await holdImport;
      data = failNextImport
        ? { successCount: 0, failCount: 1, errors: ["未找到对应合同"], message: "导入完成：成功 0 条，失败 1 条" }
        : { successCount: 1, failCount: 0, errors: [], message: "导入完成：成功 1 条" };
    } else if (endpoint === "/auth/current-permissions") data = { roles, permissions };
    else if (endpoint === "/project/all/tree") data = { records: [], total: 0, current: 1, size: 10, pages: 0 };
    else if (endpoint === "/project/all/tree/statistics") data = { allCount: 0, notStartedCount: 0, processingCount: 0, pendingAcceptanceCount: 0, completedCount: 0, archivedCount: 0, summary: zeroSummary };
    else if (endpoint === "/project/types" || endpoint.endsWith("/my-pending")) data = [];
    else if (endpoint.includes("unread-count")) data = 0;
    else if (endpoint.includes("counts")) data = {};
    else { unexpected.push(endpoint); data = []; }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ code: 200, data, msg: "success" }) });
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(base + "/all-projects");
  await page.getByRole("button", { name: "导入", exact: true }).click();
  const dialog = page.getByRole("dialog");
  const start = () => dialog.getByRole("button", { name: "开始导入", exact: true });
  const file = (name, body = "isolated mock content") => ({ name, mimeType: name.endsWith("xlsx") ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/pdf", buffer: Buffer.from(body) });
  assert(await start().isDisabled());
  await dialog.locator('input[type="file"]').nth(0).setInputFiles(file("projects.xlsx"));
  await dialog.getByText("projects.xlsx", { exact: true }).waitFor();
  await page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find((button) => button.textContent === "开始导入")?.disabled);
  assert(await start().isEnabled(), "Excel alone must enable import when contracts are optional");

  let releaseUpload;
  holdUpload = new Promise((resolve) => { releaseUpload = resolve; });
  await dialog.locator('input[type="file"]').nth(0).setInputFiles(file("contract-a.pdf"));
  await dialog.getByText("校验中", { exact: true }).waitFor();
  assert(await start().isDisabled(), "Unfinished contracts must block import");
  await page.keyboard.press("Escape");
  assert.equal(await dialog.count(), 1);
  releaseUpload(); holdUpload = null;
  await dialog.getByText("contract-a.pdf", { exact: true }).waitFor();
  await start().waitFor({ state: "visible" });
  await page.waitForFunction(() => !Array.from(document.querySelectorAll('button')).find((button) => button.textContent === "开始导入")?.disabled);
  let releaseImport;
  holdImport = new Promise((resolve) => { releaseImport = resolve; });
  await start().click();
  await dialog.getByRole("button", { name: "导入中...", exact: true }).waitFor();
  await page.keyboard.press("Escape");
  assert.equal(await dialog.count(), 1);
  assert(await dialog.getByRole("button", { name: "关闭", exact: true }).isDisabled());
  assert.equal(imports.length, 1);
  assert.deepEqual(imports[0], { fileId: "1000", contractFileIds: ["1001"] });
  releaseImport(); holdImport = null;
  await dialog.getByText("导入完成：成功 1 条", { exact: true }).waitFor();
  assert(await dialog.getByRole("button", { name: "已导入", exact: true }).isDisabled());
  await page.screenshot({ path: path.join(output, "import-complete.png") });
  await dialog.getByRole("button", { name: "关闭", exact: true }).last().click();
  await dialog.waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "导入", exact: true }).click();
  assert.equal(await dialog.getByText("projects.xlsx", { exact: true }).count(), 0);
  assert.equal(await dialog.getByText("contract-a.pdf", { exact: true }).count(), 0);
  assert(await start().isDisabled());
  await dialog.locator('input[type="file"]').nth(0).setInputFiles(file("projects.xlsx"));
  await dialog.getByText("projects.xlsx", { exact: true }).waitFor();
  await dialog.locator('input[type="file"]').nth(0).setInputFiles([file("same.pdf", "A"), file("same.pdf", "B")]);
  await dialog.getByRole("alert").filter({ hasText: "合同文件名重复" }).waitFor();
  assert(await start().isDisabled());
  assert.equal(imports.length, 1);
  await page.setViewportSize({ width: 375, height: 667 });
  await page.waitForFunction(() => document.querySelector('[role="dialog"]').getBoundingClientRect().width <= innerWidth, undefined, { timeout: 2000 });
  await page.screenshot({ path: path.join(output, "mobile-import.png"), fullPage: true });
  const mobileBounds = await dialog.evaluate((element) => ({ width: element.getBoundingClientRect().width,
    viewport: innerWidth, cssWidth: getComputedStyle(element).width, minWidth: getComputedStyle(element).minWidth,
    maxWidth: getComputedStyle(element).maxWidth, className: element.className }));
  assert(mobileBounds.width <= mobileBounds.viewport, JSON.stringify(mobileBounds));
  await dialog.getByRole("button", { name: "关闭", exact: true }).last().click();
  await dialog.waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "导入", exact: true }).click();
  await dialog.locator('input[type="file"]').nth(0).setInputFiles(file("projects.xlsx"));
  await dialog.getByText("projects.xlsx", { exact: true }).waitFor();
  await dialog.locator('input[type="file"]').nth(0).setInputFiles(file("contract-b.pdf"));
  await dialog.getByText("contract-b.pdf", { exact: true }).waitFor();
  failNextImport = true;
  await start().click();
  const failedResult = dialog.getByText("导入完成：成功 0 条，失败 1 条", { exact: true });
  await failedResult.waitFor();
  assert((await failedResult.getAttribute("class")).includes("text-rose-600"), "All-failed import must not look successful");
  await dialog.getByRole("button", { name: "关闭", exact: true }).last().click();
  await dialog.waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "导入", exact: true }).click();
  const engineeringFormats = ["GBQ7", "GDLPB6", "GEC5", "GTJ", "GJG", "GQI"];
  const contractInput = dialog.locator('input[type="file"]').nth(1);
  const accepted = (await contractInput.getAttribute("accept")).split(",");
  for (const extension of engineeringFormats) assert(accepted.includes("." + extension.toLowerCase()));
  assert.equal(await dialog.locator('input[type="file"]').nth(0).getAttribute("accept"), ".xlsx,.xls");
  await contractInput.setInputFiles(engineeringFormats.map((extension) => ({
    name: "estimate." + extension, mimeType: "application/octet-stream", buffer: Buffer.from("isolated engineering upload fixture"),
  })));
  await page.waitForFunction(() => document.querySelector('[role="dialog"] input[type="file"]')?.disabled === false);
  for (const extension of engineeringFormats) {
    await dialog.getByText("estimate." + extension, { exact: true }).waitFor();
    assert([...sessions.values()].some((session) => session.file.displayName === "estimate." + extension && session.file.fileType === "contract"));
  }
  assert(await start().isDisabled(), "Engineering attachments must not replace the Excel workbook");
  await dialog.getByRole("button", { name: "关闭", exact: true }).last().click();
  await dialog.waitFor({ state: "hidden" });
  await page.getByRole("button", { name: "导入", exact: true }).click();
  await dialog.locator('input[type="file"]').first().setInputFiles(file("without-contract.xlsx"));
  await dialog.getByText("without-contract.xlsx", { exact: true }).waitFor();
  failNextImport = false;
  await start().click();
  await dialog.getByText("导入完成：成功 1 条", { exact: true }).waitFor();
  assert.deepEqual(imports.at(-1).contractFileIds, [], "Optional contracts must also work in the actual import request");
  assert.deepEqual(errors, []);
  assert.deepEqual(unexpected, []);
  console.log(JSON.stringify({ uploadsAndImportMocked: true, contractOptional: true, unfinishedUploadBlocked: true,
    importCloseBlocked: true, repeatedImportBlocked: true, contractIdsSubmitted: true, resetsOnClose: true, duplicateNamesBlocked: true, failedImportMarked: true,
    engineeringFormatsAccepted: engineeringFormats, excelRestrictionPreserved: true, errors, output }));
} finally {
  await context.close();
  await browser.close();
}
