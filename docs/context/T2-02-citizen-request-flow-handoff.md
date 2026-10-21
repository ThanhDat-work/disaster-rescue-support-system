# T2-02 - Citizen Rescue Request Flow Handoff

- **Status:** Ready for review
- **Updated:** 2026-10-21
- **Task:** T2-02
- **Branch:** `feat/t2-02-citizen-request-flow`
- **Code merge commit:** `ab5e48106ae0088e241dd701e5d51b926ba6ee40`
- **Merged main baseline:** `f2c7462d35f3eb84af01b7e7c0833549d2ada0b1`
- **Contract:** `docs/context/T2-02-citizen-request-flow-contract.md`

## 1. Delivered Scope

T2-02 delivers the citizen vertical slice for rescue requests:

- Create a rescue request from active disaster types.
- List the authenticated citizen's requests with bounded pagination.
- View owner-scoped request details, real status history, and existing media.
- Cancel owner requests only while `PENDING` or `VERIFIED`.
- Enforce JWT-cookie authentication, `ROLE_CITIZEN`, and CSRF.
- Preserve optimistic locking and append one real cancellation history row.
- Provide desktop, tablet, and mobile responsive UI states.

## 2. Routes

- `GET /requests`
- `GET /requests/new`
- `POST /requests`
- `GET /requests/{requestId}`
- `POST /requests/{requestId}/cancel`

All routes remain under the existing `/requests/**` security matcher and require
`ROLE_CITIZEN`. Anonymous users are redirected to login. Authenticated users
without the citizen role receive 403.

## 3. Security and Ownership

- Owner UUID comes only from `AuthenticatedUserPrincipal.userId()`.
- Client-supplied owner, status, or display-code values are ignored.
- List queries are filtered by authenticated owner in the repository.
- Detail, history, media, and cancellation recheck ownership in the service.
- Missing requests and other-owner requests return the same generic not-found
  behavior to avoid request enumeration.
- Data-changing requests require valid CSRF tokens.

## 4. Creation Rules

- New status is always `PENDING`.
- Urgency is limited to `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- Disaster type must exist and be active.
- Optional text fields are trimmed and blank values become null.
- Display code is `REQ-` plus 16 uppercase hexadecimal characters.
- Collision generation is bounded to 10 attempts; the database unique
  constraint remains the final concurrency guard.
- Creation does not add a fake `PENDING -> PENDING` history row.

## 5. Listing and Detail

- Page size is 10.
- Negative pages map to page 0.
- Overflow pages map to the final available page.
- Ordering is `createdAt DESC, id DESC`.
- History ordering is `createdAt ASC, id ASC`.
- Only media rows with `deleted_at IS NULL` are returned.
- Citizen navigation contains one create link and one list link; the obsolete
  `/requests/my-requests` link was removed after merging current main.

## 6. Cancellation

- Allowed old statuses: `PENDING`, `VERIFIED`.
- Rejected statuses: `ASSIGNED`, `IN_PROGRESS`, `RESCUED`, `CLOSED`,
  `REJECTED`, and `CANCELLED`.
- Reason is required, trimmed, and limited to 2,000 characters at both DTO and
  service boundaries.
- Successful cancellation changes status to `CANCELLED` and writes exactly one
  `StatusHistory` row with the actual old status, owner, and reason.
- Repeated cancellation is rejected without a duplicate history row.
- `RescueRequest.version` and transaction flush reject stale concurrent writes.

## 7. Media Boundary

T2-02 displays existing non-deleted media only. When a request has no media, the
media section is intentionally not rendered, so no empty frame appears.

Upload, object storage, file validation/scanning, and upload lifecycle are not
implemented because no upload/storage contract exists in the current scope.
They require a separate approved task before implementation.

## 8. Main Synchronization

The feature branch merged current main commit `f2c7462`, which contains T3-01.
The following overlapping files were resolved by preserving both feature sets:

- `DisasterTypeRepository`
- `RescueRequestRepository`
- `NoDatabaseRepositoryMocks`
- shared navigation

Repository methods from both T2-02 and T3-01 remain present exactly once.
Flyway migrations V1 through V4 are the resulting database baseline. T2-02 adds
no new migration.

## 9. Automated Verification

All of the following passed on Java 17:

- Targeted `CitizenRequestServiceTest` and
  `CitizenRequestControllerMvcTest`.
- Full Maven unit-test suite.
- `mvnw clean verify` with the `it` profile and MySQL.
- Dedicated `CitizenRequestFlowIntegrationIT` using a real JWT cookie, CSRF,
  seeded MySQL data, ownership checks, cancellation transaction checks, and an
  optimistic-lock conflict.
- `git diff --check` for both working and staged merge changes.
- GitHub Actions `CI Workflow / Build and Test` on code commit `ab5e481`.

Automated negative paths include validation, missing CSRF, wrong role, missing
request, other owner, invalid urgency/type, pagination boundaries, display-code
collision, blank/oversized cancellation reason, invalid cancellation states,
repeated cancellation, and stale entity version.

## 10. Manual Browser Verification

Reviewer-observed manual verification passed for:

- Anonymous redirect to login.
- Authenticated non-citizen forbidden response.
- Empty request-list state.
- Cross-owner detail and cancellation protection.
- Create-form validation.
- Valid create and generated `PENDING` request.
- List, detail, and real history rendering.
- Blank cancellation rejection.
- Valid cancellation and success alert.
- Repeated cancellation protection.
- No cancellation action for an `ASSIGNED` request.
- Desktop, tablet, and mobile responsive layouts.
- No duplicate citizen navigation and no obsolete request-list route.

## 11. Important Files

- `CitizenRequestController`
- `CitizenRequestService`
- `CreateCitizenRequest`
- `CancelCitizenRequest`
- citizen request form/list/detail templates
- owner-scoped repository methods for requests, histories, and media
- `CitizenRequestServiceTest`
- `CitizenRequestControllerMvcTest`
- `CitizenRequestFlowIntegrationIT`
- `T2-02-citizen-request-flow-contract.md`

## 12. Downstream Contract

- Downstream request workflows must keep owner identity server-controlled.
- State changes must preserve optimistic locking and append the real transition
  to `StatusHistory` in the same transaction.
- Citizen self-cancellation remains limited to `PENDING` and `VERIFIED`.
- Any future cancellation after assignment must coordinate assignment release
  and request history atomically.
- Media upload must not be added without an approved storage/security contract.

## 13. Final Review Gate

- Code merge commit pushed: PASS.
- Code commit GitHub Actions: PASS.
- Automated MySQL verification: PASS.
- Manual browser and responsive verification: PASS.
- Working tree after code push: CLEAN.
- Remaining action after this handoff commit: confirm its documentation-only CI,
  complete PR review, and merge the PR.