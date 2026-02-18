-- ============================================================
-- Executive Real-Estate Dashboard – Database Migration
-- Run this in Supabase SQL Editor
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- 1. IMPORTS
-- ============================================================
CREATE TABLE IF NOT EXISTS imports (
  id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  uploaded_by   uuid        REFERENCES auth.users(id) ON DELETE SET NULL,
  uploaded_at   timestamptz NOT NULL DEFAULT now(),
  file_type     text        NOT NULL CHECK (file_type IN ('TR5','TR6','HK')),
  source_filename text      NOT NULL,
  storage_path  text        NOT NULL,
  file_hash     text        UNIQUE NOT NULL,
  period_from   date,
  period_to     date,
  rows_parsed   int         NOT NULL DEFAULT 0,
  rows_unmapped int         NOT NULL DEFAULT 0,
  status        text        NOT NULL DEFAULT 'imported' CHECK (status IN ('imported','error')),
  error_message text
);

-- ============================================================
-- 2. ENTRIES
-- ============================================================
CREATE TABLE IF NOT EXISTS entries (
  id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
  import_id        uuid        NOT NULL REFERENCES imports(id) ON DELETE CASCADE,
  class            int         NOT NULL CHECK (class IN (5,6)),
  doc_type         text,
  doc_no           text,
  date_duup        date,
  account          text,
  account_name     text,
  counter_account  text,
  partner          text,
  description      text,
  amount           numeric     NOT NULL,
  currency         text        NOT NULL DEFAULT 'EUR',
  property_guess   text,
  created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS entries_import_id_idx ON entries(import_id);
CREATE INDEX IF NOT EXISTS entries_date_duup_idx ON entries(date_duup);
CREATE INDEX IF NOT EXISTS entries_account_idx   ON entries(account);

-- ============================================================
-- 3. MAPPING RULES
-- ============================================================
CREATE TABLE IF NOT EXISTS mapping_rules (
  id                uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
  priority          int     NOT NULL DEFAULT 100,
  is_active         boolean NOT NULL DEFAULT true,
  applies_to_class  int     CHECK (applies_to_class IN (5,6)),
  account_prefix    text,
  account_exact     text,
  text_contains     text,
  partner_contains  text,
  kpi_group         text    NOT NULL,  -- Revenue/OPEX/G&A/CAPEX/Depreciation/Interest/Tax/Other
  kpi_line          text    NOT NULL,
  sign              int     NOT NULL DEFAULT 1 CHECK (sign IN (-1,1)),
  note              text,
  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS mapping_rules_priority_idx ON mapping_rules(priority, is_active);

-- ============================================================
-- 4. CLASSIFIED ENTRIES
-- ============================================================
CREATE TABLE IF NOT EXISTS classified_entries (
  entry_id         uuid    PRIMARY KEY REFERENCES entries(id) ON DELETE CASCADE,
  rule_id          uuid    REFERENCES mapping_rules(id) ON DELETE SET NULL,
  kpi_group        text    NOT NULL,
  kpi_line         text    NOT NULL,
  signed_amount    numeric NOT NULL,
  classified_at    timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS classified_entries_kpi_group_idx ON classified_entries(kpi_group);
CREATE INDEX IF NOT EXISTS classified_entries_kpi_line_idx  ON classified_entries(kpi_line);

-- ============================================================
-- 5. KPI_AGG_MONTH
-- ============================================================
CREATE TABLE IF NOT EXISTS kpi_agg_month (
  id          uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
  month       date    NOT NULL,  -- first day of month
  property    text    NOT NULL DEFAULT 'DEFAULT',
  kpi_group   text    NOT NULL,
  kpi_line    text    NOT NULL,
  amount      numeric NOT NULL,
  UNIQUE (month, property, kpi_group, kpi_line)
);

CREATE INDEX IF NOT EXISTS kpi_agg_month_month_idx    ON kpi_agg_month(month);
CREATE INDEX IF NOT EXISTS kpi_agg_month_property_idx ON kpi_agg_month(property);

-- ============================================================
-- 6. STAGING ERRORS
-- ============================================================
CREATE TABLE IF NOT EXISTS staging_errors (
  id          uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
  import_id   uuid    NOT NULL REFERENCES imports(id) ON DELETE CASCADE,
  page_no     int,
  raw_text    text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- ============================================================
-- RLS POLICIES
-- ============================================================

ALTER TABLE imports          ENABLE ROW LEVEL SECURITY;
ALTER TABLE entries          ENABLE ROW LEVEL SECURITY;
ALTER TABLE mapping_rules    ENABLE ROW LEVEL SECURITY;
ALTER TABLE classified_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE kpi_agg_month    ENABLE ROW LEVEL SECURITY;
ALTER TABLE staging_errors   ENABLE ROW LEVEL SECURITY;

-- Helper: check role from app_metadata
CREATE OR REPLACE FUNCTION auth.user_role() RETURNS text
  LANGUAGE sql STABLE
  AS $$
    SELECT COALESCE(
      (auth.jwt() -> 'app_metadata' ->> 'role'),
      'viewer'
    );
  $$;

-- kpi_agg_month: Viewers + Admins can read
CREATE POLICY "kpi_agg_month_read" ON kpi_agg_month
  FOR SELECT USING (auth.uid() IS NOT NULL);

-- classified_entries: Viewers + Admins can read
CREATE POLICY "classified_entries_read" ON classified_entries
  FOR SELECT USING (auth.uid() IS NOT NULL);

-- mapping_rules: Everyone can read, only admin can write
CREATE POLICY "mapping_rules_read" ON mapping_rules
  FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "mapping_rules_write" ON mapping_rules
  FOR ALL USING (auth.user_role() = 'admin');

-- imports: Admin can see all, viewer sees nothing (for raw data privacy)
CREATE POLICY "imports_admin" ON imports
  FOR ALL USING (auth.user_role() = 'admin');

-- entries: Admin only
CREATE POLICY "entries_admin" ON entries
  FOR ALL USING (auth.user_role() = 'admin');

-- staging_errors: Admin only
CREATE POLICY "staging_errors_admin" ON staging_errors
  FOR ALL USING (auth.user_role() = 'admin');

-- ============================================================
-- STORAGE BUCKET (run after creating the bucket in Supabase UI)
-- ============================================================
-- INSERT INTO storage.buckets (id, name, public)
-- VALUES ('imports', 'imports', false)
-- ON CONFLICT DO NOTHING;

-- CREATE POLICY "imports_bucket_admin" ON storage.objects
--   FOR ALL USING (
--     bucket_id = 'imports' AND auth.user_role() = 'admin'
--   );

-- ============================================================
-- SEED: STARTER MAPPING RULES
-- ============================================================
INSERT INTO mapping_rules (priority, is_active, applies_to_class, account_prefix, account_exact, text_contains, partner_contains, kpi_group, kpi_line, sign, note)
VALUES
  -- TR6 Revenue rules
  (10,  true, 6, '602',  NULL, 'nájom',           NULL, 'Revenue',     'Rent',              1,  'Rental income on account 602'),
  (15,  true, 6, NULL,   NULL, 'parking',          NULL, 'Revenue',     'Parking',           1,  'Parking revenue'),
  (20,  true, 6, NULL,   NULL, 'služby nájomcom',  NULL, 'Revenue',     'Services_To_Tenants', 1, 'Services billed to tenants'),
  (90,  true, 6, '6',    NULL, NULL,               NULL, 'Revenue',     'Other_Revenue',     1,  'Catch-all class 6'),

  -- TR5 Cost rules
  (10,  true, 5, NULL,  '551000', NULL,             NULL, 'Depreciation','Depreciation',    -1, 'Account 551000 – depreciation'),
  (20,  true, 5, '568',  NULL,    NULL,             NULL, 'G&A',         'Bank_Fees',       -1, 'Bank charges prefix 568'),
  (30,  true, 5, NULL,   NULL,    'poist',          NULL, 'OPEX',        'Insurance',       -1, 'Insurance keyword'),
  (35,  true, 5, NULL,   NULL,    'daň z nehnute',  NULL, 'OPEX',        'Property_Tax',    -1, 'Real-estate tax'),
  (40,  true, 5, NULL,   NULL,    'vod',            NULL, 'OPEX',        'Utilities',       -1, 'Water utilities'),
  (41,  true, 5, NULL,   NULL,    'elektr',         NULL, 'OPEX',        'Utilities',       -1, 'Electricity'),
  (42,  true, 5, NULL,   NULL,    'plyn',           NULL, 'OPEX',        'Utilities',       -1, 'Gas'),
  (50,  true, 5, NULL,   NULL,    'audit',          NULL, 'G&A',         'Professional_Fees',-1, 'Audit fees'),
  (51,  true, 5, NULL,   NULL,    'účtovní',        NULL, 'G&A',         'Professional_Fees',-1, 'Accounting fees'),
  (52,  true, 5, NULL,   NULL,    'advok',          NULL, 'G&A',         'Professional_Fees',-1, 'Legal fees'),
  (60,  true, 5, NULL,   NULL,    'rekonštr',       NULL, 'CAPEX',       'Capex',           -1, 'Reconstruction/CAPEX'),
  (61,  true, 5, NULL,   NULL,    'rekonstr',       NULL, 'CAPEX',       'Capex',           -1, 'Reconstruction/CAPEX (no accent)'),
  (70,  true, 5, NULL,   NULL,    'oprav',          NULL, 'OPEX',        'Repairs_Maintenance',-1, 'Repairs and maintenance'),
  (80,  true, 5, '518',  NULL,    NULL,             NULL, 'OPEX',        'Services',        -1, 'Services prefix 518'),
  (85,  true, 5, '562',  NULL,    NULL,             NULL, 'Interest',    'Interest',        -1, 'Interest expense prefix 562'),
  (86,  true, 5, '591',  NULL,    NULL,             NULL, 'Tax',         'Income_Tax',      -1, 'Income tax 591'),
  (87,  true, 5, '592',  NULL,    NULL,             NULL, 'Tax',         'Income_Tax',      -1, 'Income tax 592'),
  (95,  true, 5, '5',    NULL,    NULL,             NULL, 'G&A',         'Other',           -1, 'Catch-all class 5')
ON CONFLICT DO NOTHING;
