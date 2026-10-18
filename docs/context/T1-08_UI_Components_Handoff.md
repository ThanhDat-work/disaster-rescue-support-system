# T1-08 — UI Components Handoff

- Updated: 2026-10-18
- Branch: `feat/t1-08-ui-components`
- Implementation commit verified: `609b82c` (`fix: validate UI showcase form`)
- Showcase route: `http://localhost:8080/dev/ui-showcase` (requires the `dev` profile).

## Validation changes

- The disaster type select is required and named. Native browser validation blocks the main form before POST when it is empty. The `invalid` event adds `is-invalid` and `aria-invalid="true"` only after an invalid submit; a valid change clears both.
- The “Xem lỗi demo” button uses `formnovalidate`, so it posts independently to `/dev/ui-showcase/demo/error`. A valid main form posts to `/dev/ui-showcase/demo/request`.
- The UI guideline distinguishes showcase browser validation from DTO/server-side validation required for business operations.

## Verification on `609b82c`

- `.\mvnw.cmd clean test`: PASS — 7 tests, 0 failures, 0 errors.
- GitHub Actions `CI Workflow / build`: PASS on `609b82c`.
- GitHub Actions `Build and Test`: PASS on `609b82c`.
- Manual browser review at 390 × 844: global horizontal overflow, mobile navbar, invalid and valid form submissions, independent error demo, modal cancel/confirm behavior, and success/error alerts with five-second auto-close all PASS.
- Mobile screenshot: `evidence/t1-08/mobile-Edge-390x844.png` — viewport-only image at 390 × 844, without DevTools. Horizontal scrolling inside `.table-responsive` is expected; the full page has no horizontal overflow.

## Shared infrastructure notes

- `.github/workflows/ci.yml` runs Maven verification against MySQL in GitHub Actions; it is shared CI infrastructure.
- `.gitignore` keeps local configuration/secrets and generated artifacts out of version control; it is shared repository configuration.
- `mvnw.cmd` is the Windows Maven Wrapper entry point used for reproducible local commands; it is shared build infrastructure.
- Spring Boot does not load `.env` by itself. Database environment variables must be set in the PowerShell process or loaded into that process by an external tool. Never record secret values here.

## Scope

No T1-07 schema, migration, entity, repository, or database contract changes are included.
