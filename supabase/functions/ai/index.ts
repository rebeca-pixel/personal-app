// kind: "digest" (admin) | "motivation" (employee) | "monthly" (cron, service role). Claude key stays server-side.
import { createClient } from "npm:@supabase/supabase-js@2";
const URL_ = Deno.env.get("SUPABASE_URL")!, SRK = Deno.env.get("SERVICE_ROLE_KEY")!;
const sb = createClient(URL_, SRK);
async function claude(system: string, user: string) {
  const r = await fetch("https://api.anthropic.com/v1/messages", { method: "POST",
    headers: { "x-api-key": Deno.env.get("ANTHROPIC_API_KEY")!, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({ model: "claude-sonnet-5-5", max_tokens: 700, system, messages: [{ role: "user", content: user }] }) });
  return (await r.json()).content?.[0]?.text ?? "";
}
const TONE = "You are a warm, positive, constructive executive assistant. Never harsh, never surveillance-like. Be concise.";
async function stats(uid: string, from: string) {
  const [att, tasks, lv, qs, ans] = await Promise.all([
    sb.from("attendance").select("hours").eq("user_id", uid).gte("work_date", from),
    sb.from("pms_tasks").select("status").eq("assignee_id", uid),
    sb.from("leave_requests").select("from_date,to_date").eq("user_id", uid).eq("status", "approved").gte("from_date", from),
    sb.from("questions").select("id,target_user_id"), sb.from("answers").select("question_id").eq("user_id", uid)]);
  const mine = (qs.data ?? []).filter((q) => !q.target_user_id || q.target_user_id === uid).length;
  const t = tasks.data ?? [], done = t.filter((x) => x.status === "done").length;
  return { hours: (att.data ?? []).reduce((s, a) => s + Number(a.hours), 0), daysPresent: att.data?.length ?? 0,
    tasksAssigned: t.length, tasksDone: done, planAccuracyPct: t.length ? Math.round(100 * done / t.length) : 0,
    leaveRequests: lv.data?.length ?? 0, responseRatePct: mine ? Math.round(100 * (ans.data?.length ?? 0) / mine) : 100 };
}
Deno.serve(async (req) => {
  const { kind } = await req.json();
  const jwt = req.headers.get("Authorization")?.replace("Bearer ", "") ?? "";
  const month = new Date(); month.setDate(1); const from = month.toISOString().slice(0, 10);
  if (jwt === SRK && kind === "monthly") {
    const { data: emps } = await sb.from("profiles").select("id,full_name,fcm_token").eq("role", "employee"); const team: any[] = [];
    for (const e of emps ?? []) {
      const s = await stats(e.id, from); team.push({ name: e.full_name, ...s });
      const body = await claude(TONE, `Write a short monthly report for ${e.full_name} from these stats: ${JSON.stringify(s)}. Include 2 strengths and ONE area to improve, positive tone.`);
      await sb.from("reports").insert({ user_id: e.id, month: from, body });
    }
    const body = await claude(TONE, `Write a team comparison report for the admin from: ${JSON.stringify(team)}`);
    await sb.from("reports").insert({ user_id: null, month: from, body });
    return Response.json({ ok: true });
  }
  const { data: u } = await sb.auth.getUser(jwt); if (!u.user) return new Response("unauthorized", { status: 401 });
  const { data: me } = await sb.from("profiles").select("role,full_name").eq("id", u.user.id).single();
  if (kind === "digest" && me?.role === "admin") {
    const since = new Date(Date.now() - 864e5).toISOString();
    const { data } = await sb.from("answers").select("answer_text,profiles(full_name),questions(text)").gte("created_at", since);
    const { data: pl } = await sb.from("plans").select("plan_text,evening_text,profiles(full_name)").eq("plan_date", since.slice(0, 10));
    return Response.json({ text: await claude(TONE, `Summarise today for the admin: key points, blockers, who needs attention.\nAnswers: ${JSON.stringify(data)}\nPlans: ${JSON.stringify(pl)}`) });
  }
  if (kind === "motivation") {
    const { data: ups } = await sb.from("task_updates").select("status,created_at").eq("user_id", u.user.id).order("created_at", { ascending: false }).limit(60);
    const s = await stats(u.user.id, from);
    return Response.json({ text: await claude(TONE, `One short motivating message (2 sentences) for ${me?.full_name}, based ONLY on real data: ${JSON.stringify(s)}, recent updates ${JSON.stringify(ups?.slice(0, 20))}. Mention a streak, personal best or milestone only if the data supports it.`) });
  }
  return new Response("bad request", { status: 400 });
});
