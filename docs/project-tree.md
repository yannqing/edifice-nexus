# Project List Hierarchy

The My Projects and All Projects screens use dedicated read-only tree endpoints:

- `GET /project/{mine|all}/tree`: paginated groups, never a flat page grouped in the browser.
- `GET /project/{mine|all}/tree/{id}/children`: independently paginated matching children.
- `GET /project/{mine|all}/tree/statistics`: group status facets plus a filtered `summary`.

Existing flat project endpoints remain unchanged for project selectors, inspections, output allocations,
and other consumers. No database migration or business data rewrite is required.

## Visibility and Filtering

`mine` always requires explicit project membership, even for administrators. `all` requires the existing
All Projects menu permission or super-administrator role. Membership of a parent does not grant access
to its children, and child membership does not grant access to its parent or siblings.

A group whose parent is not readable contains only the grouping ID, matching child count, and matching
self flag. Its `project` is null; the UI shows a restricted group header with no parent details/actions.
The existing project detail and write authorization checks remain in force.

Filtering is applied to visible records before grouping and pagination. A matching child retains its
parent group even if the parent has another status or type. Searching a readable parent's name/code
also finds its visible children; unreadable parent names cannot be used to infer a relationship.
Only filtered children are returned on expansion. Archive and level filters apply to each record.

Status facets count distinct groups having a visible matching record in that status; facets can overlap
when a parent and child have different statuses. `allCount` includes archived records. Facets retain
keyword/type/level filters, but ignore the selected status/archive tab.

The nested `summary` uses exactly the same filters and permissions as the list, before pagination.
Its group count, child count, status counts and separate parent/child contract amounts therefore match
the current result set. Archived contracts contribute when the current archive filter includes them.
Context-only parents do not contribute amounts. Only readable contracts contribute, with no parent/child
amount roll-up. Changing a status/archive filter refreshes the summary as well as the list.

The top-level checkbox selects only readable parent/independent records on the current page. Selecting
a parent never implicitly selects its children. Expansion and child pagination do not open details or
clear selection. Applying a new search or filter clears selection, including selections made during
the search debounce window. Root pagination also clears selection.

## Excel Import

Project creation still requires a contract main file. The import dialog uploads the workbook and
contract files through the existing unified upload service. `POST /project/import-file` accepts
`fileId` and `contractFileIds`; the legacy multipart endpoint also accepts `contractFileIds`.
Both endpoints return `successCount`, `failCount`, `errors`, and `message` rather than an unstructured
success message. Import still requires the All Projects permission or the super-administrator role.

The workbook's `合同主文件名` column matches the original name of an uploaded contract, including its
extension. Each contract must belong to the importing user and be completed, undeleted, and permitted
by the existing upload policy. Duplicate contract names and ambiguous mappings are rejected. Only a
single-project workbook with one contract may omit the filename column for compatibility with old
templates. Missing files fail the affected row without bypassing the contract requirement.

## Verification

- Backend: `mvn -Dtest='*,!EdificeCoreApplicationTests' test` in `apps/edifice-core`.
- Frontend: `pnpm exec tsc --noEmit` and `pnpm build` in `apps/edifice-vision`.
- Browser regression: build with `NEXT_PUBLIC_API_BASE_URL=/api`, then start Next on localhost port 3310.
  Run `pnpm run test:project-tree:browser`. It requires Playwright and an installed Chromium browser.
  `PLAYWRIGHT_MODULE` can point to a supplied Playwright module; `CHROME_EXECUTABLE` can select an existing
  Chrome executable. `BASE_URL` may select another localhost frontend. No dependency is needed at runtime.
- Import browser regression: `pnpm run test:project-import:browser` with the same local server and
  Playwright options. Upload and import endpoints are fully mocked; no files or projects are created
  on a backend. Checks cover required contracts, pending uploads, duplicate names, request file IDs,
  close/reopen state, import locking, failure results, and mobile bounds.

Browser checks intercept all API requests with isolated fixtures and abort external network requests.
They cover collapsed defaults, child/root pagination, view switching, search, restricted parents,
read-only child details, retries, independent export selection, and desktop/mobile bounds. Screenshots
and results go to a temporary directory, not the repository. These are not production write tests.
