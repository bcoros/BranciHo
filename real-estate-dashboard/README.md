# Executive Real-Estate Dashboard

A production-ready financial intelligence dashboard for real-estate portfolios, powered by **Kross Omega** accounting exports (TR5/TR6 PDFs).

Built with **Next.js 15 App Router · TypeScript · Tailwind CSS · Supabase**.

---

## Features

| Area | Details |
|------|---------|
| **Auth** | Supabase email/password login, role-based access (Admin / Viewer) |
| **Dashboard** | KPI strip (NOI, OPEX, CAPEX, Net Result), NOI trend chart, P&L table, top expenses/revenue |
| **Year Comparison** | Compare any year vs 2023 / 2024 / 2025 / 2026 side-by-side |
| **PDF Import** | Drag-and-drop TR5 (costs) and TR6 (revenue) PDFs, robust parsing |
| **Classification** | Mapping rules engine (priority-ordered), auto re-classify after each import |
| **Unmapped Queue** | Review unclassified entries, assign once or save as new rule |
| **PDF Export** | One-click executive summary PDF (PDFKit, server-side) |
| **CSV Export** | Drill-down detail export to CSV |

---

## Quick Start

### 1. Clone & install

```bash
git clone <repo>
cd real-estate-dashboard
npm install
```

### 2. Create a Supabase project

1. Go to [supabase.com](https://supabase.com) → New project.
2. Copy your **Project URL** and **anon key** from *Settings → API*.
3. Copy the **Service Role key** (keep this secret).

### 3. Configure environment variables

```bash
cp .env.local.example .env.local
```

Edit `.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://xxxx.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJ...
SUPABASE_SERVICE_ROLE_KEY=eyJ...
```

### 4. Run the database migration

In the Supabase Dashboard → SQL Editor, run the full contents of:

```
db/migration.sql
```

This creates all tables, RLS policies, indexes, and seeds the starter mapping rules.

### 5. Create a Storage bucket

In Supabase Dashboard → Storage → New bucket:
- Name: `imports`
- Public: **No**

### 6. Create users and assign roles

In Supabase Dashboard → Authentication → Users, create users, then set their role via SQL:

```sql
-- Make a user an admin (replace the UUID)
UPDATE auth.users
SET raw_app_meta_data = raw_app_meta_data || '{"role": "admin"}'
WHERE id = 'USER-UUID-HERE';

-- Viewer (default, no change needed)
```

### 7. Start development server

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) — you'll be redirected to `/login`.

---

## Project Structure

```
real-estate-dashboard/
├── app/
│   ├── login/                  # Login page
│   ├── dashboard/              # Executive dashboard
│   │   ├── page.tsx            # Main dashboard (KPIs, charts, tables)
│   │   └── detail/             # Drill-down detail view
│   ├── admin/
│   │   ├── uploads/            # PDF import management
│   │   ├── mapping/            # Mapping rules CRUD
│   │   └── unmapped/           # Unmapped entries queue
│   └── api/
│       ├── export-pdf/         # Server-side PDF generation
│       └── export-csv/         # CSV export
├── components/
│   ├── ui/                     # Navbar
│   └── dashboard/              # KPI strip, charts, tables, filters
├── lib/
│   ├── supabase/               # Client, server, middleware, types
│   ├── auth.ts                 # requireAuth / requireAdmin helpers
│   ├── pdf-parser.ts           # TR5/TR6 PDF parsing logic
│   ├── classifier.ts           # Classification engine
│   ├── dashboard-data.ts       # Dashboard data queries
│   └── utils.ts                # Currency/percent formatters
├── db/
│   └── migration.sql           # Full DB schema + seed rules
└── scripts/
    └── seed-rules.ts           # Optional: seed via ts-node
```

---

## Data Flow

```
PDF Upload → Parse (pdf-parse) → entries table
                                      ↓
                               Classification engine
                               (mapping_rules, priority ASC)
                                      ↓
                    ┌─────────────────┴──────────────────┐
               classified_entries                  staging_errors
                                      ↓
                              kpi_agg_month
                                      ↓
                              Dashboard charts & KPIs
```

---

## Accounting PDF Format (Kross Omega / OMEGA)

### TR6 – Revenue (Trieda 6)
Expected columns: `DocType DocNo Date Account Description Amount`

Example line:
```
OF  2025001  15.03.2025  602100  - nájom 3/2025 - Hlavná 45  1 200,00
```

### TR5 – Costs (Trieda 5)
Expected columns: `DocNo Date Side CounterAccount Description Amount`

Example line:
```
5001  15.03.2025  MD  221000  Telekomunikačné služby - internet  250,00
```

The parser is heuristic and falls back to looser patterns. Unparseable rows go to `staging_errors`.

---

## KPI Groups & Lines

| Group | Lines |
|-------|-------|
| Revenue | Rent, Parking, Services_To_Tenants, Other_Revenue |
| OPEX | Insurance, Property_Tax, Utilities, Repairs_Maintenance, Services |
| G&A | Bank_Fees, Professional_Fees, Office_IT, Marketing, Other |
| CAPEX | Capex |
| Depreciation | Depreciation |
| Interest | Interest |
| Tax | Income_Tax |

---

## Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `NEXT_PUBLIC_SUPABASE_URL` | Yes | Supabase project URL |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Yes | Supabase anon public key |
| `SUPABASE_SERVICE_ROLE_KEY` | Yes | Service role key (server-only, never expose to client) |

---

## Deployment

### Vercel (recommended)

```bash
npm install -g vercel
vercel
```

Set the three env vars in the Vercel project settings.

### Self-hosted

```bash
npm run build
npm start
```

---

## Year Comparison

The dashboard supports comparing any year against 2023, 2024, or 2025:

1. Select the **primary year** in the Year dropdown.
2. Select a **comparison year** in the vs dropdown.
3. All KPI tiles, P&L table, and the NOI chart will show delta values.

Data is aggregated in `kpi_agg_month` and recomputed automatically after every import or mapping update.
