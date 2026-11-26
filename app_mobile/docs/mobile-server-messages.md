# Localised mobile API messages

The Flutter app presents translations for documented Spring mobile outcomes. It
does not modify API statuses, codes or response bodies; it does not translate chat
content, personal data, or unknown server details. This is a client-side display
override: Spring still returns its existing response format and language, and no
translation service is contacted.

- `AdminControllerMgmtMobile`: login `TOTP_REQUIRED`, `TOTP_INVALID`, `LOCKED`
  and `ACCOUNT_ALREADY_LOGGED_IN_ON_ANOTHER_DEVICE`; the legacy bad-credentials
  response has no code, so only the exact known message is mapped.
- `EnrollmentConfirmControllerMobile`: pending/confirm failures are mapped by
  HTTP status in the existing enrollment flow; the status and retry behaviour are
  unchanged.
- `TotpDisableControllerMobile`: 422 `TOTP_REPLAYED`, 422 invalid code and 429
  lock with `retryAfterSeconds` use the existing response handling.
- Chat REST: documented `ChatException.code` values are mapped; unknown codes
  retain the original server message. HTTP 5xx details remain hidden from the UI.

Translations are selected using the current app language and fall back to
English for unsupported locales. The chat list can re-render a retained API error in the selected language;
transient errors already stored as strings do not change until the next operation. This does not cover every server
message, WebSocket event, or all mobile UI strings.

## Checks

With Flutter installed, create `.env` from `.env.example` only if absent, then:

```sh
flutter pub get
flutter analyze
flutter test
```

On a test device, exercise the six languages with bad credentials, TOTP required,
invalid/replayed TOTP, rate limiting and the chat permission errors. Verify that
an unknown chat error still displays its original message and that HTTP status,
retry counts, user messages and secrets are not modified. The connection banner observes Spring requests. On startup, on resume and when
the user retries, it also calls the existing public auto-login endpoint without
credentials; a 401 is treated as a reachable server, not a login attempt.
