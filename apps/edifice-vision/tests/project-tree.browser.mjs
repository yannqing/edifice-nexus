import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { createRequire } from "node:module";
const load = createRequire(import.meta.url);
const { chromium } = load(process.env.PLAYWRIGHT_MODULE || "playwright");
const base = process.env.BASE_URL || "http://127.0.0.1:3310";
assert(
  ["127.0.0.1", "localhost"].includes(new URL(base).hostname),
  "Browser fixtures must only run against a local frontend",
);
const output = fs.mkdtempSync(path.join(os.tmpdir(), "edifice-tree-qa-"));
const user = {
  userId: "2111111111111111101",
  username: "tree-qa",
  realName: "层级测试员",
  status: 1,
  employmentStatus: 1,
};
const permissions = ["menu:my-projects", "menu:all-projects", "menu:workbench"];
const roles = [
  { roleId: "1", roleCode: "SUPER_ADMIN", roleName: "系统管理员" },
];
const type = {
  projectTypeId: "1",
  projectTypeName: "全程结算服务",
  projectTypeCode: "A",
  projectTypeStatus: 1,
};
const rootId = "2111111111111111111",
  privateId = "2111111111111111199";
const roots = Array.from({ length: 13 }, (_, index) =>
  make(
    String(BigInt(rootId) + BigInt(index)),
    index === 0 ? "总项目 A" : "独立项目 " + (index + 1),
  ),
);
const children = Array.from({ length: 6 }, (_, index) =>
  make(
    String(BigInt(rootId) + 1000n + BigInt(index)),
    index === 1 ? "目标子项目 Needle" : "结算子项目 A" + (index + 1),
    rootId,
  ),
);
const privateChild = make("2111111111111112299", "我参与的子项目", privateId);
const privateRoot = make(privateId, "禁止泄露的总项目");
children[4].projectStatus = 4;
children[5].archiveStatus = 1;
roots[4].projectName =
  "长项目名称用于验证文本不会遮挡展开按钮和其他字段".repeat(5);
children[2].projectName =
  "结算子项目 A3 " + "LongUnbrokenProjectName".repeat(6);
const calls = [],
  errors = [],
  unexpected = [];
let failChildren = 0,
  failRoots = 0,
  slowNeedle = false;
function make(id, name, parent = null) {
  const stages = [
    {
      projectStageId: id + "1",
      projectId: id,
      stageName: "项目策划",
      stageStatus: 1,
      stageOutput: 4,
      completionRatio: 20,
    },
  ];
  return {
    projectId: id,
    parentProjectId: parent,
    projectName: name,
    projectCode: "PJ" + id.slice(-5),
    projectType: type,
    projectStatus: 1,
    projectStage: stages[0],
    projectStages: stages,
    contractAmount: {
      contractId: id,
      contractAmount: parent ? 120000 : 1000000,
      contractType: 1,
      baseAmount: 100000,
      benefitAmount: 20000,
      preStartDate: "2026-10-01",
      preEndDate: "2027-10-01",
    },
    projectMemberList: [
      {
        projectMemberId: id,
        projectId: id,
        userId: user.userId,
        projectRoleId: "101",
        realName: user.realName,
      },
    ],
    preStartTime: null,
    preEndTime: null,
    fileCount: 3,
    archiveStatus: 0,
  };
}
function paged(records, url) {
  const current = Number(url.searchParams.get("current") || 1),
    size = Number(url.searchParams.get("pageSize") || 10);
  return {
    records: records.slice((current - 1) * size, current * size),
    current,
    size,
    total: records.length,
    pages: Math.ceil(records.length / size),
  };
}
function match(item, url) {
  const keyword = url.searchParams.get("keywords") || "",
    level = url.searchParams.get("projectLevel"),
    status = url.searchParams.get("projectStatus"),
    archive = url.searchParams.get("archiveStatus");
  return (
    (!keyword ||
      item.projectName.includes(keyword) ||
      item.projectCode.includes(keyword) ||
      (item.parentProjectId === rootId && "总项目 A".includes(keyword))) &&
    (!level || (level === "1") === !!item.parentProjectId) &&
    (!status || String(item.projectStatus) === status) &&
    (archive === null || (archive === "1") === (item.archiveStatus === 1))
  );
}
function treeStatistics(scope, url) {
  const items = [...roots, ...children, privateChild, ...(scope === "all" ? [privateRoot] : [])];
  const facetUrl = new URL(url);
  facetUrl.searchParams.delete("projectStatus");
  facetUrl.searchParams.delete("archiveStatus");
  const facetItems = items.filter((item) => match(item, facetUrl));
  const filtered = items.filter((item) => match(item, url));
  const groups = (records) => new Set(records.map((item) => item.parentProjectId || item.projectId)).size;
  const statusCount = (records, status) => groups(records.filter((item) => item.projectStatus === status));
  const active = facetItems.filter((item) => item.archiveStatus !== 1);
  const amount = (records) => records.reduce((sum, item) => sum + item.contractAmount.contractAmount, 0);
  return {
    allCount: groups(facetItems),
    notStartedCount: statusCount(active, 0),
    processingCount: statusCount(active, 1),
    pendingAcceptanceCount: statusCount(active, 2),
    completedCount: statusCount(active, 4),
    archivedCount: groups(facetItems.filter((item) => item.archiveStatus === 1)),
    summary: {
      totalCount: groups(filtered),
      childCount: filtered.filter((item) => item.parentProjectId).length,
      processingCount: statusCount(filtered, 1),
      pendingAcceptanceCount: statusCount(filtered, 2),
      completedCount: statusCount(filtered, 4),
      rootContractAmount: amount(filtered.filter((item) => !item.parentProjectId)),
      childContractAmount: amount(filtered.filter((item) => item.parentProjectId)),
    },
  };
}
async function setup(context) {
  await context.addCookies([
    { name: "access_token", value: "isolated-tree-qa", url: base },
  ]);
  await context.addInitScript(
    ({ user, roles, permissions }) => {
      localStorage.setItem("access_token", "isolated-tree-qa");
      localStorage.setItem("user_info", JSON.stringify(user));
      localStorage.setItem("user_roles", JSON.stringify(roles));
      localStorage.setItem("user_permissions", JSON.stringify(permissions));
    },
    { user, roles, permissions },
  );
  await context.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== base) return route.abort();
    if (!url.pathname.startsWith("/api/")) return route.continue();
    const p = url.pathname.slice(4);
    calls.push({
      method: req.method(),
      path: p,
      query: Object.fromEntries(url.searchParams),
    });
    let data,
      code = 200,
      msg = "success";
    const scope = p.includes("/all/") ? "all" : "mine";
    if (/\/project\/(mine|all)\/tree$/.test(p)) {
      if (failRoots) {
        failRoots--;
        code = 500;
        msg = "隔离测试：根列表加载失败";
      } else {
        const nodes = roots
          .map((root) => ({
            projectId: root.projectId,
            project: root,
            selfMatches: match(root, url),
            childCount:
              root.projectId === rootId
                ? children.filter((c) => match(c, url)).length
                : 0,
          }))
          .filter((node) => node.selfMatches || node.childCount);
        if (match(privateChild, url))
          nodes.push({
            projectId: privateId,
            project: scope === "all" ? privateRoot : null,
            selfMatches: false,
            childCount: 1,
          });
        if (slowNeedle && url.searchParams.get("keywords") === "Needle")
          await new Promise((resolve) => setTimeout(resolve, 600));
        data = paged(nodes, url);
      }
    } else if (/\/tree\/\d+\/children$/.test(p)) {
      if (failChildren) {
        failChildren--;
        code = 500;
        msg = "隔离测试：子项目加载失败";
      } else
        data = paged(
          (p.includes(rootId) ? children : [privateChild]).filter((child) =>
            match(child, url),
          ),
          url,
        );
    } else if (p.endsWith("/tree/statistics")) data = treeStatistics(scope, url);
    else if (p === "/project/types") data = [type];
    else if (p === "/auth/current-permissions") data = { roles, permissions };
    else if (p.startsWith("/project/details/")) {
      const id = p.split("/").pop(),
        project = [...roots, ...children, privateChild].find(
          (x) => x.projectId === id,
        );
      assert(project);
      data = { ...project, contract: project.contractAmount };
    } else if (p === "/project-file-folders") data = [];
    else if (p === "/project-files/list" || p === "/project-files/drafts")
      data = paged([], url);
    else if (p === "/project-files/draft-policy")
      data = { retentionDays: 30, maxBatchFiles: 200 };
    else if (p.endsWith("/benefit-history")) data = [];
    else if (p.includes("/settlements"))
      data = {
        projects: paged([], url),
        summary: {
          projectCount: 0,
          completedCount: 0,
          contractAmount: 0,
          benefitAmount: 0,
        },
        canCreate: false,
      };
    else if (p.includes("unread-count")) data = 0;
    else if (p.includes("counts")) data = {};
    else if (p.endsWith("/my-pending")) data = [];
    else {
      unexpected.push(p);
      data = [];
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ code, data, msg }),
    });
  });
}
async function stable(page) {
  await page.waitForLoadState("networkidle");
}
async function bounds(page) {
  const result = await page.evaluate(() => ({
    viewport: innerWidth,
    scroll: document.documentElement.scrollWidth,
  }));
  assert(result.scroll <= result.viewport + 1, JSON.stringify(result));
  return result;
}
(async () => {
  const browser = await chromium.launch({
    headless: true,
    executablePath: process.env.CHROME_EXECUTABLE || undefined,
  });
  const context = await browser.newContext({
    viewport: { width: 1365, height: 960 },
  });
  await setup(context);
  const page = await context.newPage();
  page.on("pageerror", (e) => errors.push(e.message));
  try {
    await page.goto(base + "/my-projects");
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "查看结算子项目 A1详情", exact: true })
        .count(),
      0,
    );
    assert(!calls.some((c) => c.path.endsWith("/children")));
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .click();
    const area = page.getByRole("region", { name: "总项目 A的子项目" });
    await area
      .getByRole("button", { name: "结算子项目 A1", exact: false })
      .first()
      .waitFor();
    assert.equal(await page.getByRole("dialog").count(), 0);
    await area
      .getByRole("button", { name: "下一页子项目", exact: true })
      .click();
    await area
      .getByRole("button", { name: "结算子项目 A6", exact: false })
      .first()
      .waitFor();
    assert.equal(
      await area
        .getByRole("button", { name: "查看结算子项目 A1详情", exact: true })
        .count(),
      0,
    );
    await page.screenshot({ path: path.join(output, "cards-expanded.png") });
    await bounds(page);
    await page.getByRole("button", { name: "列表视图", exact: true }).click();
    await page.getByRole("table").waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "收起总项目 A的子项目", exact: true })
        .count(),
      1,
    );
    await page.screenshot({ path: path.join(output, "mine-list.png") });
    await bounds(page);
    await page
      .getByRole("textbox", { name: "搜索项目", exact: true })
      .fill("Needle");
    await page
      .getByRole("region", { name: "总项目 A的子项目" })
      .getByRole("button", { name: "查看目标子项目 Needle详情", exact: true })
      .waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "收起总项目 A的子项目", exact: true })
        .count(),
      1,
    );
    await page
      .getByRole("button", { name: "查看目标子项目 Needle详情", exact: true })
      .click();
    await page.getByRole("dialog").waitFor();
    await stable(page);
    assert(
      calls.some((c) => c.path === "/project/details/" + children[1].projectId),
    );
    await page.keyboard.press("Escape");
    await page.getByRole("dialog").waitFor({ state: "hidden" });
    await page.getByRole("textbox", { name: "搜索项目" }).fill("");
    await stable(page);
    slowNeedle = true;
    await page.getByRole("textbox", { name: "搜索项目" }).fill("Needle");
    await page.waitForTimeout(350);
    await page.getByRole("textbox", { name: "搜索项目" }).fill("独立项目 2");
    await stable(page);
    await page
      .getByRole("button", { name: "查看独立项目 2详情", exact: true })
      .waitFor();
    await page.waitForTimeout(800);
    assert.equal(
      await page
        .getByRole("button", { name: "查看目标子项目 Needle详情", exact: true })
        .count(),
      0,
    );
    slowNeedle = false;
    await page.getByRole("button", { name: "清除筛选", exact: true }).click();
    await stable(page);
    await page
      .getByRole("button", { name: "下一页项目组", exact: true })
      .click();
    await stable(page);
    assert.equal(
      await page
        .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
        .count(),
      0,
    );
    await page
      .getByRole("button", { name: "展开所属总项目的子项目", exact: true })
      .click();
    await page
      .getByRole("button", { name: "查看我参与的子项目详情", exact: true })
      .waitFor();
    assert.equal(
      await page.getByText("禁止泄露的总项目", { exact: true }).count(),
      0,
    );
    await page.screenshot({ path: path.join(output, "restricted-parent.png") });
    await page.goto(base + "/all-projects");
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .waitFor();
    failChildren = 1;
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .click();
    await page.getByRole("button", { name: "重试加载子项目" }).waitFor();
    await page.getByRole("button", { name: "重试加载子项目" }).click();
    await page
      .getByRole("button", { name: "查看结算子项目 A1详情", exact: true })
      .waitFor();
    await page
      .getByRole("checkbox", { name: "选择总项目 A", exact: true })
      .check();
    assert.equal(
      await page
        .getByRole("checkbox", { name: "选择结算子项目 A1", exact: true })
        .isChecked(),
      false,
    );
    await page
      .getByRole("checkbox", { name: "选择结算子项目 A1", exact: true })
      .check();
    await page.getByText("已选择 2 个项目", { exact: true }).waitFor();
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .count();
    await page.screenshot({ path: path.join(output, "all-expanded.png") });
    await bounds(page);
    assert(await page.getByRole("table").evaluate(table => table.scrollWidth <= table.parentElement.clientWidth + 1), "Desktop actions must stay visible even with long project names");

    // A record selected during the debounce window must not survive the effective search change.
    await page.getByRole("button", { name: "取消选择", exact: true }).click();
    await page.clock.install();
    await page.clock.pauseAt(new Date());
    await page.getByRole("textbox", { name: "搜索项目" }).fill("Needle");
    await page.getByRole("checkbox", { name: "选择结算子项目 A1", exact: true }).check();
    await page.getByText("已选择 1 个项目", { exact: true }).waitFor();
    await page.clock.runFor(350);
    await page.clock.resume();
    await page.getByRole("button", { name: "查看目标子项目 Needle详情", exact: true }).waitFor();
    assert.equal(await page.getByRole("checkbox", { name: "选择结算子项目 A1", exact: true }).count(), 0);
    assert.equal(await page.getByRole("button", { name: "批量导出", exact: true }).count(), 0);

    const summaryValue = (label) => page.getByText(label, { exact: true }).locator("..").locator("p").last();
    const childMoney = page.getByText("子项目合同额", { exact: true }).locator("..").locator("p").last();
    const waitSummary = async (status, archive) => {
      await page.waitForResponse((response) => {
        const url = new URL(response.url());
        return url.pathname.endsWith("/all/tree/statistics") &&
          url.searchParams.get("projectStatus") === status &&
          url.searchParams.get("archiveStatus") === archive;
      });
      await stable(page);
    };
    await page.getByRole("button", { name: "清除筛选", exact: true }).click();
    await page.getByRole("button", { name: "展开总项目 A的子项目", exact: true }).waitFor();
    const completedSummary = waitSummary("4", "0");
    await page.getByRole("combobox", { name: "项目状态", exact: true }).selectOption("4");
    await completedSummary;
    assert.equal(await summaryValue("项目组数").innerText(), "1");
    assert.equal(await summaryValue("子项目数").innerText(), "1");
    assert.equal(await childMoney.innerText(), "¥120,000");
    const archivedSummary = waitSummary(null, "1");
    await page.getByRole("combobox", { name: "项目状态", exact: true }).selectOption("archived");
    await archivedSummary;
    assert.equal(await summaryValue("项目组数").innerText(), "1");
    assert.equal(await summaryValue("子项目数").innerText(), "1");
    assert.equal(await childMoney.innerText(), "¥120,000");
    await page.getByRole("button", { name: "清除筛选", exact: true }).click();
    await stable(page);
    failRoots = 1;
    await page.getByRole("textbox", { name: "搜索项目" }).fill("failed");
    await page
      .getByRole("alert")
      .filter({ hasText: "根列表加载失败" })
      .waitFor();
    await page.getByRole("button", { name: "重试", exact: true }).click();
    await stable(page);
    assert.equal(
      await page
        .getByRole("alert")
        .filter({ hasText: "根列表加载失败" })
        .count(),
      0,
    );
    await page.goto(base + "/my-projects");
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .waitFor();
    await page.setViewportSize({ width: 375, height: 667 });
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .click();
    await page
      .getByRole("button", { name: "查看结算子项目 A1详情", exact: true })
      .waitFor();
    await bounds(page);
    await page.screenshot({
      path: path.join(output, "mobile-card.png"),
      fullPage: true,
    });
    await page.getByRole("button", { name: "列表视图", exact: true }).click();
    await bounds(page);
    await page.screenshot({
      path: path.join(output, "mobile-list.png"),
      fullPage: true,
    });
    await page.goto(base + "/all-projects");
    await page
      .getByRole("button", { name: "展开总项目 A的子项目", exact: true })
      .waitFor();
    await bounds(page);
    assert.deepEqual(errors, []);
    assert.deepEqual(unexpected, []);
    assert(calls.every((call) => call.method === "GET"));
    const result = {
      desktopAndMobile: true,
      cardsAndLists: true,
      rootPagination: true,
      childPagination: true,
      searchAutoExpand: true,
      restrictedParent: true,
      retries: true,
      independentSelection: true,
      debouncedSelectionReset: true,
      filteredSummary: true,
      errors,
      unexpected,
      calls: calls.length,
      output,
    };
    fs.writeFileSync(
      path.join(output, "result.json"),
      JSON.stringify(result, null, 2),
    );
    console.log(JSON.stringify(result));
  } finally {
    await context.close();
    await browser.close();
  }
})().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
