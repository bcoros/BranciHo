/**
 * Seed script: inserts starter mapping rules into Supabase.
 * Run: npx ts-node scripts/seed-rules.ts
 *
 * Requires env vars: NEXT_PUBLIC_SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY
 */

import { createClient } from '@supabase/supabase-js'

const supabase = createClient(
  process.env.NEXT_PUBLIC_SUPABASE_URL!,
  process.env.SUPABASE_SERVICE_ROLE_KEY!
)

const rules = [
  // TR6 Revenue
  { priority: 10,  is_active: true, applies_to_class: 6, account_prefix: '602',  text_contains: 'nájom',           kpi_group: 'Revenue',     kpi_line: 'Rent',              sign: 1,  note: 'Rental income on 602' },
  { priority: 15,  is_active: true, applies_to_class: 6, text_contains: 'parking',                                  kpi_group: 'Revenue',     kpi_line: 'Parking',           sign: 1,  note: 'Parking revenue' },
  { priority: 20,  is_active: true, applies_to_class: 6, text_contains: 'služby nájomcom',                           kpi_group: 'Revenue',     kpi_line: 'Services_To_Tenants', sign: 1, note: 'Services to tenants' },
  { priority: 90,  is_active: true, applies_to_class: 6, account_prefix: '6',                                        kpi_group: 'Revenue',     kpi_line: 'Other_Revenue',     sign: 1,  note: 'Catch-all class 6' },
  // TR5 Costs
  { priority: 10,  is_active: true, applies_to_class: 5, account_exact: '551000',                                    kpi_group: 'Depreciation',kpi_line: 'Depreciation',      sign: -1, note: 'Depreciation 551000' },
  { priority: 20,  is_active: true, applies_to_class: 5, account_prefix: '568',                                      kpi_group: 'G&A',         kpi_line: 'Bank_Fees',         sign: -1, note: 'Bank charges 568' },
  { priority: 30,  is_active: true, applies_to_class: 5, text_contains: 'poist',                                     kpi_group: 'OPEX',        kpi_line: 'Insurance',         sign: -1, note: 'Insurance' },
  { priority: 35,  is_active: true, applies_to_class: 5, text_contains: 'daň z nehnute',                             kpi_group: 'OPEX',        kpi_line: 'Property_Tax',      sign: -1, note: 'Real-estate tax' },
  { priority: 40,  is_active: true, applies_to_class: 5, text_contains: 'vod',                                       kpi_group: 'OPEX',        kpi_line: 'Utilities',         sign: -1, note: 'Water' },
  { priority: 41,  is_active: true, applies_to_class: 5, text_contains: 'elektr',                                    kpi_group: 'OPEX',        kpi_line: 'Utilities',         sign: -1, note: 'Electricity' },
  { priority: 42,  is_active: true, applies_to_class: 5, text_contains: 'plyn',                                      kpi_group: 'OPEX',        kpi_line: 'Utilities',         sign: -1, note: 'Gas' },
  { priority: 50,  is_active: true, applies_to_class: 5, text_contains: 'audit',                                     kpi_group: 'G&A',         kpi_line: 'Professional_Fees', sign: -1, note: 'Audit fees' },
  { priority: 51,  is_active: true, applies_to_class: 5, text_contains: 'účtovní',                                   kpi_group: 'G&A',         kpi_line: 'Professional_Fees', sign: -1, note: 'Accounting fees' },
  { priority: 52,  is_active: true, applies_to_class: 5, text_contains: 'advok',                                     kpi_group: 'G&A',         kpi_line: 'Professional_Fees', sign: -1, note: 'Legal fees' },
  { priority: 60,  is_active: true, applies_to_class: 5, text_contains: 'rekonštr',                                  kpi_group: 'CAPEX',       kpi_line: 'Capex',             sign: -1, note: 'Reconstruction' },
  { priority: 61,  is_active: true, applies_to_class: 5, text_contains: 'rekonstr',                                  kpi_group: 'CAPEX',       kpi_line: 'Capex',             sign: -1, note: 'Reconstruction (no accent)' },
  { priority: 70,  is_active: true, applies_to_class: 5, text_contains: 'oprav',                                     kpi_group: 'OPEX',        kpi_line: 'Repairs_Maintenance',sign: -1, note: 'Repairs' },
  { priority: 80,  is_active: true, applies_to_class: 5, account_prefix: '518',                                      kpi_group: 'OPEX',        kpi_line: 'Services',          sign: -1, note: 'Services 518' },
  { priority: 85,  is_active: true, applies_to_class: 5, account_prefix: '562',                                      kpi_group: 'Interest',    kpi_line: 'Interest',          sign: -1, note: 'Interest 562' },
  { priority: 86,  is_active: true, applies_to_class: 5, account_prefix: '591',                                      kpi_group: 'Tax',         kpi_line: 'Income_Tax',        sign: -1, note: 'Tax 591' },
  { priority: 87,  is_active: true, applies_to_class: 5, account_prefix: '592',                                      kpi_group: 'Tax',         kpi_line: 'Income_Tax',        sign: -1, note: 'Tax 592' },
  { priority: 95,  is_active: true, applies_to_class: 5, account_prefix: '5',                                        kpi_group: 'G&A',         kpi_line: 'Other',             sign: -1, note: 'Catch-all class 5' },
]

async function main() {
  console.log('Seeding mapping rules…')

  const { data, error } = await supabase
    .from('mapping_rules')
    .upsert(rules as Parameters<ReturnType<typeof supabase.from>['upsert']>[0], { onConflict: 'priority' })
    .select()

  if (error) {
    console.error('Error:', error.message)
    process.exit(1)
  }

  console.log(`✓ Seeded ${data?.length ?? 0} rules.`)
}

main()
