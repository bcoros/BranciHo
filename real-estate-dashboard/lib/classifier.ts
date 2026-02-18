/**
 * Classification engine: matches entries against mapping rules,
 * writes classified_entries, recomputes kpi_agg_month.
 */
import type { SupabaseClient } from '@supabase/supabase-js'
import type { Database, Entry, MappingRule } from '@/lib/supabase/types'

type DB = Database

function matchRule(entry: Entry, rule: MappingRule): boolean {
  if (!rule.is_active) return false

  if (rule.applies_to_class !== null && rule.applies_to_class !== entry.class)
    return false

  if (rule.account_exact !== null && entry.account !== rule.account_exact)
    return false

  if (rule.account_prefix !== null) {
    const acc = entry.account ?? ''
    if (!acc.startsWith(rule.account_prefix)) return false
  }

  if (rule.text_contains !== null) {
    const desc = (entry.description ?? '').toLowerCase()
    if (!desc.includes(rule.text_contains.toLowerCase())) return false
  }

  if (rule.partner_contains !== null) {
    const partner = (entry.partner ?? '').toLowerCase()
    if (!partner.includes(rule.partner_contains.toLowerCase())) return false
  }

  return true
}

export async function classifyImport(
  supabase: SupabaseClient<DB>,
  importId: string
) {
  // Load all active rules ordered by priority ASC
  const { data: rules, error: rErr } = await supabase
    .from('mapping_rules')
    .select('*')
    .eq('is_active', true)
    .order('priority', { ascending: true })

  if (rErr || !rules) throw new Error(`Failed to load mapping rules: ${rErr?.message}`)

  // Load all entries for this import
  const { data: entries, error: eErr } = await supabase
    .from('entries')
    .select('*')
    .eq('import_id', importId)

  if (eErr || !entries) throw new Error(`Failed to load entries: ${eErr?.message}`)

  // Delete existing classifications for this import's entries
  const entryIds = entries.map((e) => e.id)
  if (entryIds.length > 0) {
    await supabase
      .from('classified_entries')
      .delete()
      .in('entry_id', entryIds)
  }

  const classified: Database['public']['Tables']['classified_entries']['Insert'][] = []
  let unmappedCount = 0

  for (const entry of entries) {
    let matchedRule: MappingRule | null = null
    for (const rule of rules) {
      if (matchRule(entry, rule)) {
        matchedRule = rule
        break
      }
    }

    if (matchedRule) {
      classified.push({
        entry_id: entry.id,
        rule_id: matchedRule.id,
        kpi_group: matchedRule.kpi_group,
        kpi_line: matchedRule.kpi_line,
        signed_amount: entry.amount * matchedRule.sign,
        classified_at: new Date().toISOString(),
      })
    } else {
      unmappedCount++
    }
  }

  // Batch insert classified entries
  if (classified.length > 0) {
    const CHUNK = 500
    for (let i = 0; i < classified.length; i += CHUNK) {
      const { error: cErr } = await supabase
        .from('classified_entries')
        .insert(classified.slice(i, i + CHUNK))
      if (cErr) throw new Error(`Failed to insert classified_entries: ${cErr.message}`)
    }
  }

  // Update import counts
  await supabase
    .from('imports')
    .update({
      rows_parsed: entries.length,
      rows_unmapped: unmappedCount,
    })
    .eq('id', importId)

  // Recompute kpi_agg_month for affected months
  await recomputeKpiAgg(supabase)

  return { classified: classified.length, unmapped: unmappedCount }
}

/**
 * Recomputes the entire kpi_agg_month table from classified_entries + entries.
 * For simplicity we truncate and rebuild (fits within serverless timeout for typical volumes).
 */
export async function recomputeKpiAgg(supabase: SupabaseClient<DB>) {
  // Get all classified entries joined with entries for month/property
  const { data, error } = await supabase.rpc('recompute_kpi_agg' as never)

  if (error) {
    // RPC not set up – fall back to in-app computation
    await recomputeKpiAggFallback(supabase)
  }
}

async function recomputeKpiAggFallback(supabase: SupabaseClient<DB>) {
  // Fetch all classified + entry data
  const { data: rows, error } = await supabase
    .from('classified_entries')
    .select(`
      kpi_group,
      kpi_line,
      signed_amount,
      entries!inner(date_duup, property_guess)
    `)

  if (error || !rows) return

  // Aggregate
  const agg = new Map<string, number>()

  for (const row of rows) {
    const entry = (row as { entries: { date_duup: string | null; property_guess: string | null } }).entries
    if (!entry?.date_duup) continue

    const month = entry.date_duup.substring(0, 7) + '-01'
    const property = entry.property_guess ?? 'DEFAULT'
    const key = `${month}||${property}||${row.kpi_group}||${row.kpi_line}`
    agg.set(key, (agg.get(key) ?? 0) + Number(row.signed_amount))
  }

  if (agg.size === 0) return

  const inserts = Array.from(agg.entries()).map(([key, amount]) => {
    const [month, property, kpi_group, kpi_line] = key.split('||')
    return { month, property, kpi_group, kpi_line, amount }
  })

  // Upsert in chunks
  const CHUNK = 200
  for (let i = 0; i < inserts.length; i += CHUNK) {
    await supabase
      .from('kpi_agg_month')
      .upsert(inserts.slice(i, i + CHUNK), {
        onConflict: 'month,property,kpi_group,kpi_line',
      })
  }
}

/**
 * Classify a single entry on-demand (for unmapped queue assignment).
 */
export async function classifySingleEntry(
  supabase: SupabaseClient<DB>,
  entryId: string,
  kpiGroup: string,
  kpiLine: string,
  signedAmount: number,
  ruleId?: string
) {
  await supabase
    .from('classified_entries')
    .upsert({
      entry_id: entryId,
      rule_id: ruleId ?? null,
      kpi_group: kpiGroup,
      kpi_line: kpiLine,
      signed_amount: signedAmount,
      classified_at: new Date().toISOString(),
    })

  // Update unmapped count on the parent import
  const { data: entry } = await supabase
    .from('entries')
    .select('import_id')
    .eq('id', entryId)
    .single()

  if (entry) {
    const { count } = await supabase
      .from('entries')
      .select('*', { count: 'exact', head: true })
      .eq('import_id', entry.import_id)
      .not('id', 'in',
        `(SELECT entry_id FROM classified_entries)`
      ) as unknown as { count: number }

    await supabase
      .from('imports')
      .update({ rows_unmapped: count ?? 0 })
      .eq('id', entry.import_id)
  }

  await recomputeKpiAgg(supabase)
}
