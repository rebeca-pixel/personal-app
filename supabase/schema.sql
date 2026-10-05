create table profiles (
  id uuid primary key references auth.users on delete cascade,
  full_name text not null, role text not null default 'employee' check (role in ('admin','employee')),
  fcm_token text, created_at timestamptz default now());
create table questions (
  id uuid primary key default gen_random_uuid(), text text not null,
  priority text not null default 'normal' check (priority in ('normal','urgent')),
  deadline timestamptz, target_user_id uuid references profiles(id), -- null = everyone
  created_by uuid references profiles(id), created_at timestamptz default now());
create table answers (
  id uuid primary key default gen_random_uuid(), question_id uuid references questions(id) on delete cascade,
  user_id uuid references profiles(id), answer_text text not null, created_at timestamptz default now(),
  unique(question_id, user_id));
create table plans (
  id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id),
  plan_date date not null default current_date, plan_text text, evening_text text,
  created_at timestamptz default now(), unique(user_id, plan_date));
create table reminders (   -- server-side escalation (Phase 3 scheduler uses this)
  id uuid primary key default gen_random_uuid(), question_id uuid references questions(id) on delete cascade,
  user_id uuid references profiles(id), step int default 0, next_at timestamptz, status text default 'sent');
create table reports (id uuid primary key default gen_random_uuid(), user_id uuid references profiles(id), month date, body text);

create function is_admin() returns boolean language sql security definer as
$$ select exists(select 1 from profiles where id = auth.uid() and role='admin') $$;

alter table profiles enable row level security; alter table questions enable row level security;
alter table answers enable row level security; alter table plans enable row level security;
alter table reminders enable row level security; alter table reports enable row level security;

create policy p_self on profiles for select using (id = auth.uid() or is_admin());
create policy p_upd on profiles for update using (id = auth.uid());
create policy q_read on questions for select using (is_admin() or target_user_id is null or target_user_id = auth.uid());
create policy q_admin on questions for all using (is_admin()) with check (is_admin());
create policy a_ins on answers for insert with check (user_id = auth.uid());
create policy a_read on answers for select using (user_id = auth.uid() or is_admin());
create policy pl_own on plans for all using (user_id = auth.uid() or is_admin()) with check (user_id = auth.uid());
create policy r_read on reminders for select using (user_id = auth.uid() or is_admin());
create policy rep_read on reports for select using (user_id = auth.uid() or is_admin());
