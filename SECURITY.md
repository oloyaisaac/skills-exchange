# Security Checklist — 7 Backend Vulnerability Patterns

A reusable checklist for auditing backend/API code against the 7 most common security holes. For each hole: how the attack works, and the fix.

---

## 1. SQL Injection

### How it works
The app takes whatever a user types into a search box or form field and puts it directly into a SQL query string. An attacker enters a single quote (`'`) to break out of the string literal, then appends a second payload such as `1=1`. The quote ends the app's text, so the injected `OR 1=1` runs as SQL. Because `1=1` is always true, the query returns every row in the table — exposing data the user was never meant to see.

### Fix
- Use **parameterized queries / prepared statements** everywhere. Send user input as a separate parameter value, never as part of the SQL text.
- Never concatenate user-controlled strings into queries, even inside string-building helpers or ORMs that may still interpolate.
- Whitelist acceptable inputs (e.g., an enum, a fixed set of columns) where possible.
- Apply the principle of least privilege to the database account the app uses.

---

## 2. IDOR (Insecure Direct Object Reference) — also called BOLA (Broken Object Level Authorization)

### How it works
The URL or request body points to a resource by ID — e.g., `GET /api/orders/1`. The server verifies that the caller is *logged in*, but never checks that the order `1` belongs to that caller. An attacker simply changes the ID to `2`, `3`, … and reads other users' data.

### Fix
- On every request that touches a user-owned object, verify **ownership** in addition to authentication.
- Resolve the current user from the authenticated session and compare it to the owner ID on the object before returning or mutating it.
- Fail closed: deny by default if the ownership check cannot be evaluated.

---

## 3. Mass Assignment

### How it works
A profile or settings form sends `name` and `email` fields. The server blindly copies every field it receives into the account record. An attacker adds an extra field such as `role: admin` to the request, and the server copies it right in — escalating the account without any other check.

### Fix
- **Allow-list** the fields a client may edit. Accept only those fields and ignore everything else.
- Never iterate over an incoming JSON object and write every key into a database row or mutable document.
- Use the framework's built-in mass-assignment protection (e.g., `only: [], except: []` in Rails Strong Parameters) as defense in depth.

---

## 4. Insecure Deserialization

### How it works
Deserializing turns safe bytes back into live objects. In languages like Python (pickle), Java (ObjectInputStream), or via certain serializers, a crafted payload can execute arbitrary code *while* the object is being rebuilt — before your validation code ever runs. A maliciously crafted file or request body can therefore run the attacker's command the moment the server deserializes it.

### Fix
- **Never deserialize untrusted input.** Accept only plain JSON or a format that cannot execute code.
- If a legacy binary format must be read, treat it as untrusted and add a strict schema validation step plus an integrity check (signature/message authentication) before deserialization.
- Keep deserialization libraries patched and monitor for CVEs.

---

## 5. Sensitive Data Exposure

### How it works
The API returns the entire user row — including the password hash, access tokens, internal flags, etc. The app's UI only displays the user's name, so the app looks safe. But anyone who opens the HTTP response (browser dev tools, a curl command, a proxy) can read every field.

### Fix
- Return **only the fields the screen needs** from the API. Never serialize the full database row to the client.
- Keep secrets (hashes, tokens, keys) out of logs, error messages, and API responses.
- Use HTTPS-only transport and set secure cookie flags.

---

## 6. Broken Authentication

### How it works
Login requires just a password, with no second factor and no throttle on login attempts. A bot can try millions of passwords — including credentials leaked from other sites (credential stuffing). Anyone who reused a password from another breach has their account taken over.

### Fix
- Add **MFA** (TOTP, WebAuthn, or SMS/email fallback) for sensitive actions and, ideally, for all logins.
- **Throttle or lock** login endpoints after a small number of failures (rate limiting, exponential backoff, account lockout).
- **Expire sessions** onlogout/offline and invalidate old tokens; use short-lived access tokens with refresh rotation.
- Hash passwords with a slow, salted algorithm (bcrypt, scrypt, Argon2). Enforce strong password policy and monitor for credential breaches.

---

## 7. Cross-Site Scripting (XSS)

### How it works
Someone posts a comment containing a `<script>` tag. The page inserts that comment into the HTML it serves, unchanged — so the browser executes it as code. The script runs in every visitor's browser, giving the attacker access to the site on behalf of that visitor (session cookies, tokens, page data).

### Fix
- **Encode all output** so text is rendered as text, not as markup. Use the framework's auto-escaping feature by default.
- If you must allow rich HTML, **sanitize** it with a well-audited library (e.g., DOMPurify) and apply a strict allow-list of tags and attributes.
- Set `Content-Security-Policy` headers as a compensating control.
- Defense in depth: context-aware encoding for HTML, attribute, JavaScript, and URL contexts.

---

## One Missed Check

The root cause behind all seven: **the server trusted something it should not have.** A single honest review pass — parameterizing queries, ownership checks, allow-listing input, rejecting untrusted deserialization, narrowing API responses, hardening auth, and encoding output — closes every hole on this list.
