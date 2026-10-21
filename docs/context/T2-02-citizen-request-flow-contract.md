# T2-02 - Citizen Rescue Request Flow Contract

- **Status:** Approved
- **Approved date:** 2026-10-21
- **Task:** T2-02
- **Implementation branch:** `feat/t2-02-citizen-request-flow`
- **Dependencies:** T1-08, T2-01

## 1. Scope

T2-02 implements the citizen vertical slice for creating, listing, viewing,
tracking, and cancelling rescue requests. It reuses the JWT-cookie, CSRF, RBAC,
and optimistic-locking foundations already present in the application.

## 2. Routes and Authorization

All `/requests/**` routes require an authenticated `ROLE_CITIZEN` user.

- `GET /requests`: owner-scoped paginated list.
- `GET /requests/new`: form populated with active disaster types only.
- `POST /requests`: create request; CSRF is mandatory.
- `GET /requests/{requestId}`: owner-scoped detail, history, existing media.
- `POST /requests/{requestId}/cancel`: owner cancellation; CSRF is mandatory.

Anonymous users are redirected to login. Authenticated non-citizens receive
403. UI visibility never replaces server-side authorization.

## 3. Creation Rules

- Owner identity comes only from `AuthenticatedUserPrincipal.userId()`.
- Client-supplied owner, status, and display code values are ignored.
- Initial status is always `PENDING`.
- Urgency is `LOW`, `MEDIUM`, `HIGH`, or `CRITICAL`.
- Disaster type must exist and be active.
- Display code is `REQ-` plus 16 uppercase hexadecimal characters.
- Display-code generation is retried at most 10 times; database uniqueness
  remains the final guard.
- New requests do not create a fake status-history transition.

## 4. Listing and Detail Rules

- Ownership is enforced by repository and service queries/checks.
- Page size is 10.
- Negative pages map to page 0.
- Pages above the last page map to the last available page.
- Ordering is `createdAt DESC, id DESC`.
- Missing requests and requests owned by another user use the same generic
  not-found response to avoid information disclosure.
- History ordering is `createdAt ASC, id ASC`.
- Only media rows with `deleted_at IS NULL` are shown.

## 5. Cancellation Rules

- Only `PENDING` and `VERIFIED` requests may be cancelled by their owner.
- Cancellation reason is required, trimmed, and limited to 2,000 characters.
- Request status changes to `CANCELLED` and exactly one `StatusHistory` row
  records the real old status, new status, owner, and reason in one transaction.
- Repeated cancellation is rejected and cannot create another history row.
- `RescueRequest.version` plus transaction flush enforces optimistic locking.
- Concurrent updates return a safe retry message instead of overwriting data.

## 6. Media Boundary

T2-02 displays existing non-deleted request media. Binary upload, object
storage, file scanning, and upload lifecycle are not implemented because the
repository has no approved upload/storage contract. They require a separate
task and decision.

## 7. Persistence

T2-02 reuses the request, history, media, user, and disaster-type schema from V1
and the current optimistic-locking field. After merging current main, Flyway V1
through V4 must pass. T2-02 adds no migration.

## 8. Required Verification

Automated verification covers:

- DTO/controller validation and CSRF.
- Server-owned owner, status, and display code.
- Active disaster types and urgency allowlist.
- Display-code collision retry and bounded failure.
- Pagination boundaries and owner-scoped queries.
- Detail, history, media ownership and generic not-found behavior.
- Every allowed and rejected cancellation state.
- Blank and oversized cancellation reasons.
- Repeated cancellation without duplicate history.
- Real JWT-cookie flow against MySQL.
- Role authorization and missing CSRF.
- Optimistic-lock conflict rejection.

Manual verification covers desktop and mobile create, list, detail, history,
cancellation visibility, cancellation success/error, empty state, anonymous
access, and authenticated non-citizen access.

## 9. Completion Gate

Do not commit the merged implementation until targeted tests, full unit tests,
MySQL integration verification, and the exact manual browser flow all pass.
Final handoff and evidence must contain actual final results only and must be
updated after the merge commit and GitHub Actions result are known.
