-- Run AFTER schema.sql. NOTE: pms_tasks / attendance / leave_requests are ASSUMED shapes of your PMS, TimeStrap
-- and LMS data. If your real tables differ, change these (or create views with these names/columns over yours).
alter table profiles add column if not exists phone text, add column if not exists emergency_phone text,
  add column if not exists work_start time default '09:00', add column if not exists work_end time default '18:00',
  add column if not exists shift_end time default '18:30';
alter table plans add column if not exists plan_task_ids jsonb default '[]', add column if not exists mood int;
alter table reports alter column user_id drop not null;   -- null user_id = admin team report

create table if not exists pms_tasks (id uuid primary key default gen_random_uuid(), title text not null,
  assignee_id uuid references profiles(id), status text default 'todo', progress int default 0, due_date date);
create table if not exists attendance (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  work_date date, hours numeric);
create table if not exists leave_requests (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  from_date date, to_date date, kind text default 'leave', status text default 'pending');
create table if not exists task_updates (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  task_id uuid references pms_tasks(id), status text, percent int, note text, created_at timestamptz default now());
create table if not exists blockers (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  note text, created_at timestamptz default now(), resolved boolean default false);
create table if not exists checkins (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  checkin_date date default current_date, created_at timestamptz default now(), unique(user_id, checkin_date));
create table if not exists home_alerts (user_id uuid, alert_date date default current_date, primary key(user_id, alert_date));
create table if not exists snoozes (id uuid primary key default gen_random_uuid(), question_id uuid, user_id uuid, reason text, created_at timestamptz default now());
alter table reminders add constraint reminders_uq unique (question_id, user_id);

alter table pms_tasks enable row level security; alter table attendance enable row level security;
alter table leave_requests enable row level security; alter table task_updates enable row level security;
alter table blockers enable row level security; alter table checkins enable row level security;
alter table snoozes enable row level security;
create policy t_read on pms_tasks for select using (assignee_id = auth.uid() or is_admin());
create policy t_upd on pms_tasks for update using (assignee_id = auth.uid());
create policy at_read on attendance for select using (user_id = auth.uid() or is_admin());
create policy lv_read on leave_requests for select using (user_id = auth.uid() or is_admin());
create policy tu_all on task_updates for all using (user_id = auth.uid() or is_admin()) with check (user_id = auth.uid());
create policy bl_all on blockers for all using (user_id = auth.uid() or is_admin()) with check (user_id = auth.uid());
create policy ci_all on checkins for all using (user_id = auth.uid() or is_admin()) with check (user_id = auth.uid());
create policy sn_all on snoozes for all using (user_id = auth.uid() or is_admin()) with check (user_id = auth.uid());

create or replace view admin_dashboard with (security_invoker = true) as
select p.id, p.full_name,
  exists(select 1 from plans pl where pl.user_id=p.id and pl.plan_date=current_date and pl.plan_text is not null) as plan_submitted,
  (select count(*) from questions q where (q.target_user_id is null or q.target_user_id=p.id)
     and not exists(select 1 from answers a where a.question_id=q.id and a.user_id=p.id)) as pending,
  (select count(*) from questions q where q.deadline < now() and (q.target_user_id is null or q.target_user_id=p.id)
     and not exists(select 1 from answers a where a.question_id=q.id and a.user_id=p.id)) as overdue,
  (select coalesce(max(step),0) from reminders r where r.user_id=p.id and r.step>=3
     and not exists(select 1 from answers a where a.question_id=r.question_id and a.user_id=p.id)) as ignoring_steps
from profiles p where p.role='employee';

alter table blockers add column if not exists notified boolean default false;
alter table snoozes add constraint snooze_once unique (question_id, user_id);
create policy bl_admin_upd on blockers for update using (is_admin());

-- SECURITY: stop users editing their own role / reassigning tasks (RLS alone allows any column).
revoke update on profiles from authenticated, anon;  grant update (fcm_token) on profiles to authenticated;
revoke update on pms_tasks from authenticated, anon; grant update (status, progress) on pms_tasks to authenticated;

-- Cron (enable pg_cron + pg_net): scheduler every minute, monthly report on the last day of each month.
-- select cron.schedule('scheduler','* * * * *',$$select net.http_post('https://YOUR-PROJECT.supabase.co/functions/v1/scheduler','{}'::jsonb,headers:='{"Authorization":"Bearer SERVICE_ROLE_KEY"}'::jsonb)$$);
-- select cron.schedule('monthly','0 3 28-31 * *',$$select net.http_post('https://YOUR-PROJECT.supabase.co/functions/v1/ai','{"kind":"monthly"}'::jsonb,headers:='{"Authorization":"Bearer SERVICE_ROLE_KEY"}'::jsonb) where (now() + interval '1 day')::date = date_trunc('month', now() + interval '1 day')::date$$);
