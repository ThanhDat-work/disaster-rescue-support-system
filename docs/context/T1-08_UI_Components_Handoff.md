# T1-08 UI Components Handoff

- **Updated:** 2026-10-17 (Asia/Saigon)
- **Branch:** `feat/t1-08-ui-components`
- **Starting HEAD:** `26d188f`
- **Showcase URL:** `http://localhost:8080/dev/ui-showcase`
- **Pagination examples:** `?page=0` (first), `?page=1` (middle), `?page=2` (last)
- **Route availability:** `dev` profile only. That profile grants anonymous access to the showcase path; other paths remain authenticated.
- **Verification:** `mvn -Dtest=UiShowcaseMvcTest test` — 1 test passed, covering rendered first/middle/last pages, preserved query contract, alerts, status badge, and modal.
- **Database configuration:** unchanged; `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, with no source password default.
- **Role contract:** database/authority name `ROLE_RESCUER`; Thymeleaf navbar checks `hasRole('RESCUER')`.

## Fragment contracts

- `layouts/main-layout :: layout(title, content)` — page title and a section fragment; page templates use native `th:replace`.
- `fragments/components :: status-badge(status)` — request status only; never pass urgency values.
- `fragments/components :: pagination(pageData, pageUrl, pageQuery)` — the controller/model owns the canonical path and query prefix; the fragment appends `page=N` and does not inspect the request URI.
- `fragments/components :: confirm-modal(modalId, title, message, formAction)` — confirmation dialog containing a POST form.
- `fragments/alerts :: flash-messages` — floating toast-style Bootstrap Alerts for `successMsg` and `errorMsg`; `main.js` closes alerts after five seconds.

## Render evidence

The MockMvc smoke test renders all three pagination states using the actual Thymeleaf page and fragments. Browser screenshot files for Chrome and Edge at desktop and mobile sizes remain outstanding: this session's browser automation reports no available browsers, and Edge launch through the connected UI surface is unavailable. Chrome is not installed in the detected standard location. Capture the four browser screenshots when those browser surfaces are available and add them under `docs/context/evidence/t1-08/`.

## Next action

Run the focused test or `mvn verify`, then capture desktop/mobile screenshots in Chrome and Edge and append their filenames and final commit ID here.
