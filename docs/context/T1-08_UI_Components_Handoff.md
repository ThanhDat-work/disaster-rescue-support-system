# T1-08 — UI Components Handoff

- Updated: 2026-10-18
- Branch: `feat/t1-08-ui-components`
- Review base: `0fe01ffaa30ff7b284c4acdfd2a18a4c20fc02bd` (`Fix T1-08 UI showcase review findings`)
- Current changes are in the workspace and have not been committed. The commit hash and CI result for these changes do not exist yet.
- Showcase route: `http://localhost:8080/dev/ui-showcase` (requires the `dev` profile).

## Review round 2 fixes

- The disaster type select is required and has a `name`. Native browser constraint validation blocks the main form before POST when it is empty. The `invalid` event adds Bootstrap's `is-invalid` styling only after an invalid submit; a valid change clears the styling.
- The “Xem lỗi demo” submit button has `formnovalidate`, so it still posts to `/dev/ui-showcase/demo/error` independently of the required select. The valid main form posts to `/dev/ui-showcase/demo/request`.
- The UI guideline now distinguishes showcase browser validation from DTO/server-side validation required for real business operations.
- The existing page continues to use isolated `/dev/**` demo endpoints, responsive table scrolling, pagination, modal, and flash alerts.

## Verification status

- Review base `0fe01ff` previously passed `clean test` and branch CI according to the supplied review. Those results do not verify the uncommitted changes here.
- Required rerunnable Windows PowerShell command: `.\mvnw.cmd clean test`.
- After committing and pushing the fixes, check GitHub Actions on that exact commit; CI must not be described as passing before then.
- Browser evidence must be captured against the updated code. Record invalid-submit blocked, valid-submit success alert, independent error demo, modal and auto-close behavior, and mobile page width (`scrollWidth = clientWidth = 390`). Keep screenshots at the actual 390×844 viewport without cropping that could imply horizontal overflow.
- Manual browser validation and CI for the current workspace changes: pending.

## Shared infrastructure notes

- `.github/workflows/ci.yml` runs Maven verification against MySQL in GitHub Actions; it is shared CI infrastructure, so changes there should be explained and scoped carefully.
- `.gitignore` keeps local configuration/secrets and generated artifacts out of version control; it is shared repository configuration.
- `mvnw.cmd` is the Windows Maven Wrapper entry point used for reproducible local commands; it is shared build infrastructure.
- Spring Boot does not load `.env` by itself. Database environment variables must be set in the PowerShell process or loaded into that process by an external tool. Never record secret values here.

## Scope

No T1-07 schema, migration, entity, repository, or database contract changes are part of this UI validation fix. Confirm with `git diff --name-only` before handoff/merge.
