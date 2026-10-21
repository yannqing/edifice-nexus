import assert from "node:assert/strict";
import test from "node:test";
import { folderAncestors, folderOptions, relativeDirectory } from "../src/lib/project-file-folders.ts";

const folders = [
  { folderId: "2110000000000000001", parentFolderId: "0", folderName: "Reports" },
  { folderId: "2110000000000000002", parentFolderId: "2110000000000000001", folderName: "January" },
  { folderId: "2110000000000000003", parentFolderId: "0", folderName: "Drawings" },
];

test("breadcrumbs preserve snowflake ids and directory order", () => {
  assert.deepEqual(folderAncestors(folders, "2110000000000000002").map((f) => f.folderName), ["Reports", "January"]);
  assert.deepEqual(folderAncestors(folders, "0"), []);
});

test("folder choices contain full paths without coercing ids to numbers", () => {
  assert.deepEqual(folderOptions(folders), [
    { id: "2110000000000000003", path: "Drawings" },
    { id: "2110000000000000001", path: "Reports" },
    { id: "2110000000000000002", path: "Reports/January" },
  ]);
});

test("folder cycles cannot hang breadcrumb rendering", () => {
  const cycle = [{ folderId: "1", parentFolderId: "2", folderName: "A" }, { folderId: "2", parentFolderId: "1", folderName: "B" }];
  assert.equal(folderAncestors(cycle, "1").length, 2);
});

test("directory selection retains the selected top-level folder", () => {
  assert.equal(relativeDirectory("Package/Reports/January/file.pdf"), "Package/Reports/January");
  assert.equal(relativeDirectory("Package/file.pdf"), "Package");
  assert.equal(relativeDirectory("file.pdf"), "");
  assert.equal(relativeDirectory(undefined), "");
});

test("absolute, traversal, malformed and control-character paths are rejected", () => {
  for (const path of ["/root/a.pdf", "../a.pdf", "Folder/../a.pdf", "Folder//a.pdf", "C:/a.pdf", "Folder\\a.pdf", "Folder/\u0000/a.pdf"])
    assert.throws(() => relativeDirectory(path), undefined, path);
});

test("directory name, depth and path limits match backend limits", () => {
  assert.equal(relativeDirectory(`${Array(20).fill("a").join("/")}/file.pdf`).split("/").length, 20);
  assert.throws(() => relativeDirectory(`${Array(21).fill("a").join("/")}/file.pdf`));
  assert.throws(() => relativeDirectory(`${"a".repeat(129)}/file.pdf`));
  assert.throws(() => relativeDirectory(`${Array(20).fill("a".repeat(128)).join("/")}/file.pdf`));
  assert.throws(() => relativeDirectory("Package/Reports/file.pdf", 1));
  assert.throws(() => relativeDirectory("Package/file.pdf", 0));
  assert.equal(relativeDirectory("file.pdf", 0), "");
});
