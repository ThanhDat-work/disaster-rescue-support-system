# DRSS UI Components Guideline

**Project:** Disaster Rescue Support System (DRSS)
**Source of truth:** `src/main/resources/templates/layouts/main-layout.html` and the fragments under `templates/fragments/`.

## 1. Page layout and navigation

The project uses native Thymeleaf fragments. Do not use `layout:decorate` or the Thymeleaf Layout Dialect; that dependency is not installed. Start each page by replacing its root with the shared layout and pass the page section as the layout content:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{layouts/main-layout :: layout('Request list', ~{::section})}">
<section>
    <h1>Request list</h1>
    <!-- page content -->
</section>
</html>
```

`main-layout.html` renders the navbar, flash messages, page content, Bootstrap bundle, and `main.js`. The navbar role checks use `hasRole('RESCUER')`; Spring Security adds `ROLE_` automatically, matching the database role name `ROLE_RESCUER`.

## 2. Status and urgency

Request **status** and **urgency** are separate fields. Status values are `PENDING`, `VERIFIED`, `ASSIGNED`, `IN_PROGRESS`, `RESCUED`, `CLOSED`, `REJECTED`, and `CANCELLED`. `CRITICAL` is an urgency value, never a request status. `RESOLVED` is not a supported status.

Always render request statuses through the shared fragment; do not write status badge markup or inline colors:

```html
<td th:replace="~{fragments/components :: status-badge(${request.status})}"></td>
```

Show urgency separately, with text that identifies the urgency field (for example `Khẩn cấp: CRITICAL`). Do not pass urgency values to `status-badge`.

## 3. Buttons and forms

Use Bootstrap button classes and the shared tokens. Use `btn-primary` for the main action, `btn-outline-secondary` for cancel/back, and `btn-outline-danger` for destructive actions. For validation errors, mark the input with `is-invalid` and put `.invalid-feedback` directly after it.

## 4. Data tables

Put tables inside `.table-responsive`, usually within a `.card`, and use `.table.table-hover.align-middle`. This keeps wide data usable on small screens.

## 5. Confirmation modal

Reuse `confirm-modal(modalId, title, message, formAction)` from `fragments/components.html`. Trigger it with matching `data-bs-toggle="modal"` and `data-bs-target="#modalId"`; include the fragment once on the page. Pass a server-owned action URL and keep destructive actions as POST forms. Do not duplicate modal markup inline.

```html
<button type="button" class="btn btn-outline-danger"
        data-bs-toggle="modal" data-bs-target="#rejectModal">Từ chối</button>
<div th:replace="~{fragments/components :: confirm-modal('rejectModal', 'Từ chối yêu cầu', 'Xác nhận từ chối?', '/requests/reject/1')}"></div>
```

## 6. Pagination

Use `pagination(pageData, pageUrl, pageQuery)` from `fragments/components.html`. The controller supplies the `Page` model, the canonical path, and a query prefix that preserves the page's filter/sort contract and ends with `&` (or is `?` when there are no other parameters). The fragment appends `page=N`; it must not inspect the servlet request URI.

```html
<nav th:replace="~{fragments/components :: pagination(${pageData}, ${pageUrl}, ${pageQuery})}"></nav>
```

The first and last links are rendered disabled at their boundaries. Keep pagination above and below long tables when useful.

## 7. Flash messages

Controllers set `successMsg` or `errorMsg` as flash attributes after a redirect. The shared `flash-messages` fragment renders them as floating, toast-style notifications with a pulse indicator and blurred surface. They use Bootstrap Alert markup and `main.js` closes `.alert-dismissible` alerts after five seconds.

## 8. Showcase and verification

With the `dev` profile active, open `http://localhost:8080/dev/ui-showcase` (pages: `?page=0`, `?page=1`, `?page=2`). The route is disabled outside `dev`. The showcase demonstrates layout, alerts, table/status badge, form validation, modal, and pagination.
