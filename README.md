# Personal Executive — Phases 1–4 (purple & white)

## Setup (in order)
1. Supabase: run `supabase/schema.sql`, then `supabase/schema_phase2_4.sql`. Create users in Authentication and add a
   `profiles` row each (role admin/employee, plus phone / emergency_phone / work & shift times).
2. IMPORTANT: `pms_tasks`, `attendance`, `leave_requests` in schema_phase2_4.sql are ASSUMED shapes. Point them at your real
   PMS / TimeStrap / LMS data (views with the same column names work).
3. `Config.kt`: Supabase URL + anon key.
4. Firebase: add `google-services.json` to `app/`. Get a service-account JSON for FCM v1.
5. Deploy functions: `supabase functions deploy scheduler` and `supabase functions deploy ai`.
   Secrets: SERVICE_ROLE_KEY, FCM_SERVICE_ACCOUNT (JSON), ANTHROPIC_API_KEY, TWILIO_SID, TWILIO_TOKEN, TWILIO_FROM.
6. Enable pg_cron + pg_net and run the two `cron.schedule` lines at the bottom of schema_phase2_4.sql.
7. Open in Android Studio, sync, test on real budget phones (Xiaomi/Oppo/Vivo/Realme/Samsung).

## Map
- P1: login, plan, evening update, admin questions, repeat-until-answered (local alarms + server push)
- P2: PMS tasks (pick plan, progress syncs back), planned vs actual, live dashboard, LMS pending approvals
- P3: reached-home check-in (repeats until confirmed, alerts after 60 min), push -> SMS -> admin escalation, quiet hours, leave skip
- P4: AI digest, motivation from real data, monthly reports (employee + team), admin assistant tab

## Notes
- No gradle-wrapper.jar is included: open the folder in Android Studio and accept "create/sync Gradle wrapper" (Gradle 8.7).
- Blocker alerts reach the admin within ~1 minute (sent by the scheduler). Admin must have signed in once so their FCM token is saved.
- The monthly report is delivered in-app (Report tab); no push is sent when it is created.
