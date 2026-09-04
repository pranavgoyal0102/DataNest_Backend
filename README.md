# DataNest Backend

Spring Boot service backing DataNest: file metadata in Postgres, file bytes in Cloudinary,
authentication via Firebase ID tokens, and a delta-sync feed built for multiple devices holding the
same account.

- **Java 17**, Spring Boot 3.5, Maven wrapper
- **Postgres** for metadata, **Cloudinary** for stored assets
- **Firebase Admin** verifies every request

---

## Authentication

Every endpoint except `/actuator/health` requires a Firebase ID token:

```
Authorization: Bearer <firebase-id-token>
```

The caller's UID is taken **only** from the verified token. It never arrives in a path variable or a
request body, so a client cannot act as another user by editing the payload. Requests without a
token, or with one that fails verification, get `401`.

Sessions are stateless — no cookies, so CSRF protection is disabled by design.

## Configuration

| Environment variable | Property | Required | Default |
|---|---|---|---|
| `FIREBASE_CREDENTIALS` | `firebase.credentials` | see below | *(blank)* |
| `CORS_ALLOWED_ORIGINS` | `app.cors.allowed-origins` | yes | `http://localhost:5173` |
| `MAX_FILE_SIZE` | `spring.servlet.multipart.max-file-size` | no | `100MB` |
| `MAX_REQUEST_SIZE` | `spring.servlet.multipart.max-request-size` | no | `100MB` |

`FIREBASE_CREDENTIALS` is a Spring resource URL — `file:/run/secrets/firebase.json` or
`classpath:firebase.json`. Leave it blank to fall back to Application Default Credentials, i.e.
`GOOGLE_APPLICATION_CREDENTIALS`.

**These have no environment-variable indirection wired up** and must be supplied as Spring
properties — through a profile file, `SPRING_APPLICATION_JSON`, or `SPRING_*` environment variables:

| Property | Notes |
|---|---|
| `spring.datasource.url` / `.username` / `.password` | Postgres |
| `cloudinary.cloud-name` / `.api-key` / `.api-secret` | all three required; startup fails without them |

The asymmetry is real: Firebase and CORS read `${VAR:default}` in `application.properties`, the
datasource and Cloudinary values do not.

### Running locally

Create `src/main/resources/application-local.properties` — it is gitignored:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/datanest
spring.datasource.username=postgres
spring.datasource.password=<your-password>
spring.jpa.hibernate.ddl-auto=update

cloudinary.cloud-name=<cloud-name>
cloudinary.api-key=<key>
cloudinary.api-secret=<secret>

firebase.credentials=file:./secrets/<service-account>.json
app.cors.allowed-origins=http://localhost:5173
```

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

The `datanest` database must already exist; `ddl-auto=update` creates tables, never the database.
Keep the service-account JSON out of the repository — `secrets/` is gitignored.

## Endpoints

All paths are under `/api/files`. Every response is wrapped:

```json
{ "success": true, "message": "...", "data": { } }
```

| Method | Path | Query / body | Returns |
|---|---|---|---|
| `POST` | `/api/files` | body: `title`, `mimeType`, `size`, `isStarred`, `isDeleted` | the created file |
| `POST` | `/api/files/upload` | multipart `file`, optional `isStarred` | the created file |
| `GET` | `/api/files` | — | every file for the caller |
| `GET` | `/api/files/sync` | `since` \| `cursor`, `limit` | `{ changes, cursor, hasMore }` |
| `GET` | `/api/files/search` | `q` (required) | live matches, trashed excluded |
| `GET` | `/api/files/trash` | — | trashed files |
| `PATCH` | `/api/files/{id}` | body: any of `title`, `isStarred`, `isDeleted` + `version` | — |
| `POST` | `/api/files/{id}/trash` | `version` (required) | — |
| `POST` | `/api/files/{id}/restore` | `version` (required) | — |
| `DELETE` | `/api/files/{id}` | `version` (required) | — |
| `GET` | `/actuator/health` | — | unauthenticated |

A file looks like:

```json
{
  "id": "53f21914-10a8-4d07-b313-150a9e68d21c",
  "title": "notes.txt",
  "mimeType": "text/plain",
  "size": 120,
  "cloudUrl": "https://res.cloudinary.com/<cloud>/raw/upload/v1/abc123",
  "isStarred": false,
  "isDeleted": false,
  "createdAt": 1787159020453,
  "updatedAt": 1787159020453,
  "version": 0
}
```

`PATCH` is a partial update: an omitted field is left alone, not cleared. Sending
`{"isStarred": false, "version": 3}` unstars the file and leaves the title untouched.

`DELETE` is permanent — it removes the row and destroys the Cloudinary asset — and is only allowed
on a file **already in the trash**. Deleting a live file returns `409`. Trash first, then delete.

The row is deleted before the asset, and that order matters. The row delete is version-checked, so
if another device restored the file between the server's read and its write, the delete affects no
rows and fails with `409` — before anything irreversible happens. Destroying the asset first would
leave a live row pointing at bytes that no longer exist.

## Sync protocol

`GET /api/files/sync` returns everything that changed after a watermark, oldest first.

**Cold start** — pull from the beginning of time:

```
GET /api/files/sync?since=0&limit=100
```

**Every pull after that** — echo the cursor from the previous response:

```
GET /api/files/sync?cursor=<cursor>&limit=100
```

The response is:

```json
{ "changes": [ ...files... ], "cursor": "MTc4NzE1...", "hasMore": true }
```

Keep pulling while `hasMore` is true, feeding each response's `cursor` into the next request. When
`hasMore` is false you are caught up; store the cursor and use it next time.

`limit` is clamped to `[1, 500]`, default `100`. Out-of-range values are clamped, not rejected.

### Why the cursor is opaque

The cursor encodes **`(updatedAt, id)`**, not just a timestamp, and clients must treat it as an
opaque string.

`updatedAt` is millisecond precision, so rows routinely share a value. A cursor holding only a
timestamp cannot page through a group of rows tied on the same millisecond: resuming with
`updatedAt > last` **skips** whatever remains of the tied group, and resuming with `>=` **re-sends it
forever** if a whole page shares one timestamp. Pairing the timestamp with the row id gives a total
order, so every row is delivered exactly once.

Two further guarantees:

- The cursor is always derived from the **last row actually returned**, never from "now". The
  watermark cannot advance past data you did not receive.
- An empty page returns the cursor you sent, unchanged. Nothing was seen, so nothing advances.

### Tombstones

Soft-deleted files are **included** in the feed, with `isDeleted: true`. That is how deletions
propagate: on receiving one, remove the file locally. If you filter them out, deletions made on
another device will never reach this one.

## Conflict resolution

Every file carries a `version`, incremented by the server on each change.

Clients echo the version they last saw on all four mutations — `PATCH`, `trash`, `restore`, and
`DELETE`. If it does not match the server's current value, the write is rejected with **`409`**, and
the response `data` contains **the server's current state of that file**:

```json
{
  "success": false,
  "message": "Version conflict",
  "data": { "id": "...", "title": "renamed.txt", "version": 4, "...": "..." }
}
```

That body is the point of the design: the client can reconcile immediately, without a follow-up
`GET`. Merge, then retry with the version from the conflict response.

Two layers back this up. An explicit comparison catches the ordinary case of a client working from a
stale copy. Underneath, JPA's `@Version` catches the narrower race where another writer commits
between the server's read and its write — that surfaces as the same `409`.

A no-op update — one that changes nothing — does **not** bump the version and does not touch
`updatedAt`, so it will not churn other devices' sync feeds.

## Error responses

| Status | When |
|---|---|
| `400` | validation failure, missing required parameter, malformed UUID, unparseable body, bad sync cursor |
| `401` | missing, malformed, or unverifiable token |
| `404` | file does not exist **or is not yours** |
| `405` | wrong method for the path |
| `409` | version conflict, or permanent delete on a file not in the trash |
| `413` | upload exceeds the multipart limit |
| `500` | unexpected server error |

A file owned by someone else returns `404`, not `403`. That is deliberate — `403` would confirm the
row exists, letting an attacker probe for valid ids.

## Docker

```bash
docker build -t datanest-backend .

docker run --rm -p 8080:8080 \
  -v /path/to/firebase.json:/run/secrets/firebase.json:ro \
  -e FIREBASE_CREDENTIALS=file:/run/secrets/firebase.json \
  -e CORS_ALLOWED_ORIGINS=https://your-frontend \
  -e SPRING_APPLICATION_JSON='{
        "spring.datasource.url":"jdbc:postgresql://host.docker.internal:5432/datanest",
        "spring.datasource.username":"postgres",
        "spring.datasource.password":"...",
        "cloudinary.cloud-name":"...",
        "cloudinary.api-key":"...",
        "cloudinary.api-secret":"..."
      }' \
  datanest-backend
```

The image is multi-stage, runs as a non-root user, and contains **no** credentials. `.dockerignore`
keeps `secrets/` and `application-local.properties` out of the build context — they are gitignored,
which protects the repository but would not protect a `docker build`.

There is no `HEALTHCHECK` instruction because the base image ships no `curl` or `wget`. Point your
orchestrator's probe at `/actuator/health`.

## Tests

```bash
./mvnw verify
```

59 tests: a context smoke test, JPA tests against H2 covering `@Version` behaviour and sync paging,
and unit tests for the service, storage, and cursor layers. No database or credentials are needed —
H2 is in-memory and the Firebase beans are overridden.

## Known limitations

These are deliberate and documented rather than hidden.

**Permanent delete leaves no tombstone.** `DELETE` removes the row outright, so a client offline
across *both* the trash and the delete never learns the file is gone. The trash-first requirement
narrows this — the tombstone exists for the whole window between the two calls — but closing it
properly needs retained tombstone rows or a separate deletions table.

**`GET /api/files` and `/search` are unbounded.** Neither paginates. Only `/sync` does.

**Cloudinary orphans are possible.** Two paths can leave one: a failed metadata write triggers a
compensating delete which may itself fail, and a permanent delete removes the row before destroying
the asset, so a failing destroy leaves the asset behind. Both log the `public_id` so the asset can
still be found and removed by hand, but there is no reconciliation sweep.

**Assets uploaded before `publicId` was stored cannot be deleted** through the API, since the handle
needed to address them was never recorded.

