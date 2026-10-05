// Runs every minute. Repeat-until-answered push -> SMS -> admin alert, plus night "reached home" check-in.
import { createClient } from "npm:@supabase/supabase-js@2";
import { GoogleAuth } from "npm:google-auth-library@9";
const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SERVICE_ROLE_KEY")!);
const sa = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT")!);
const GAPS = [5, 15, 30, 60];
const nowIST = () => new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Kolkata" }));
const hhmm = (d: Date) => d.getHours() * 60 + d.getMinutes();
const mins = (t: string) => { const [h, m] = t.split(":").map(Number); return h * 60 + m; };

async function push(token: string | null, data: Record<string, string>) {
  if (!token) return;
  const at = await new GoogleAuth({ credentials: sa, scopes: ["https://www.googleapis.com/auth/firebase.messaging"] }).getAccessToken();
  await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST", headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
    // HIGH priority data message + 7-day TTL: delivered when an offline/switched-off phone reconnects
    body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl: "604800s" } } }) });
}
async function sms(to: string | null, body: string) {
  if (!to) return; const sid = Deno.env.get("TWILIO_SID")!, tok = Deno.env.get("TWILIO_TOKEN")!;
  await fetch(`https://api.twilio.com/2010-04-01/Accounts/${sid}/Messages.json`, { method: "POST",
    headers: { Authorization: "Basic " + btoa(`${sid}:${tok}`), "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ To: to, From: Deno.env.get("TWILIO_FROM")!, Body: body }) });
  // WhatsApp: same call with To/From prefixed "whatsapp:" (needs an approved WhatsApp sender).
}
async function onLeave(uid: string, today: string) {
  const { data } = await sb.from("leave_requests").select("id").eq("user_id", uid).eq("status", "approved")
    .lte("from_date", today).gte("to_date", today).limit(1); return !!data?.length;
}

Deno.serve(async () => {
  const ist = nowIST(), today = new Date().toLocaleDateString("en-CA", { timeZone: "Asia/Kolkata" }), t = hhmm(ist);
  const { data: people } = await sb.from("profiles").select("*");
  const emps = (people ?? []).filter((p) => p.role === "employee");
  const admin = (people ?? []).find((p) => p.role === "admin");
  const { data: qs } = await sb.from("questions").select("*");
  const { data: ans } = await sb.from("answers").select("question_id,user_id");
  const { data: rems } = await sb.from("reminders").select("*");
  const { data: snz } = await sb.from("snoozes").select("question_id,user_id").gte("created_at", new Date(Date.now() - 30 * 60000).toISOString());
  const done = new Set((ans ?? []).map((a) => a.question_id + a.user_id));

  for (const q of qs ?? []) for (const u of emps.filter((e) => !q.target_user_id || e.id === q.target_user_id)) {
    if (done.has(q.id + u.id)) { await sb.from("reminders").update({ status: "answered" }).eq("question_id", q.id).eq("user_id", u.id); continue; }
    if (snz?.some((s) => s.question_id === q.id && s.user_id === u.id)) continue;   // employee snoozed (30 min)
    const r = (rems ?? []).find((x) => x.question_id === q.id && x.user_id === u.id);
    if (r && new Date(r.next_at) > new Date()) continue;
    const urgent = q.priority === "urgent";
    const quiet = t < mins(u.work_start ?? "09:00") || t > mins(u.work_end ?? "18:00");
    if (!urgent && (quiet || await onLeave(u.id, today))) {          // quiet hours + leave awareness
      await sb.from("reminders").upsert({ question_id: q.id, user_id: u.id, step: r?.step ?? 0, status: r?.status ?? "sent",
        next_at: new Date(Date.now() + 30 * 60000).toISOString() }, { onConflict: "question_id,user_id" }); continue; }
    const step = (r?.step ?? 0) + 1;
    await push(u.fcm_token, { question_id: q.id, text: q.text, priority: q.priority });
    if (step >= 4) await sms(u.phone, `Personal Executive: please answer "${q.text}"`);          // escalation level 2
    if (step === 6) await sms(admin?.phone ?? null, `${u.full_name} has not answered: "${q.text}"`); // level 3: alert admin
    await sb.from("reminders").upsert({ question_id: q.id, user_id: u.id, step, status: "sent",
      next_at: new Date(Date.now() + GAPS[Math.min(step - 1, 3)] * 60000).toISOString() }, { onConflict: "question_id,user_id" });
  }

  // Night "reached home" check-in: push every 15 min after shift end until confirmed; alert after 60 min.
  const { data: cis } = await sb.from("checkins").select("user_id").eq("checkin_date", today);
  const { data: al } = await sb.from("home_alerts").select("user_id").eq("alert_date", today);
  for (const u of emps) {
    const end = mins(u.shift_end ?? "18:30"), since = t - end;
    if (since < 0 || since > 240 || cis?.some((c) => c.user_id === u.id) || await onLeave(u.id, today)) continue;
    if (since % 15 === 0) await push(u.fcm_token, { type: "home", question_id: "home", text: "Did you reach home safely? Tap to confirm." });
    if (since >= 60 && !al?.some((a) => a.user_id === u.id)) {
      await sms(u.emergency_phone ?? admin?.phone ?? null, `${u.full_name} has not confirmed reaching home.`);
      await sb.from("home_alerts").insert({ user_id: u.id });
    }
  }
  // Blockers: notify the admin right away (within a minute)
  const { data: bl } = await sb.from("blockers").select("id,note,profiles(full_name)").eq("notified", false);
  for (const b of (bl ?? []) as any[]) {
    await push(admin?.fcm_token ?? null, { type: "blocker", question_id: "blk-" + b.id, text: `${b.profiles?.full_name} is blocked: ${b.note}` });
    await sb.from("blockers").update({ notified: true }).eq("id", b.id);
  }
  return new Response("ok");
});
