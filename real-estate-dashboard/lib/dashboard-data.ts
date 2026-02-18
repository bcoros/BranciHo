/**
 * Dashboard data access functions
 */
import { createClient } from '@/lib/supabase/server'
import type { KpiAggMonth } from '@/lib/supabase/types'

export interface KpiFilters {
  year: number
  compareYear?: number
  property?: string
}

export interface KpiSummary {
  revenue: number
  opex: number
  capex: number
  gAndA: number
  depreciation: number
  interest: number
  tax: number
  noi: number
  noiMargin: number
  netResult: number
}

export interface MonthlyNoi {
  month: string
  noi: number
  noiCompare?: number
}

export interface PerformanceLine {
  label: string
  current: number
  compare?: number
  kpi_group: string
  kpi_line: string
}

export interface TopItem {
  label: string
  amount: number
  kpi_group: string
  kpi_line: string
}

function summarise(rows: KpiAggMonth[]): KpiSummary {
  let revenue = 0, opex = 0, capex = 0, gAndA = 0
  let depreciation = 0, interest = 0, tax = 0

  for (const r of rows) {
    switch (r.kpi_group) {
      case 'Revenue':      revenue      += Number(r.amount); break
      case 'OPEX':         opex         += Number(r.amount); break
      case 'CAPEX':        capex        += Number(r.amount); break
      case 'G&A':          gAndA        += Number(r.amount); break
      case 'Depreciation': depreciation += Number(r.amount); break
      case 'Interest':     interest     += Number(r.amount); break
      case 'Tax':          tax          += Number(r.amount); break
    }
  }

  const noi = revenue + opex  // opex is already negative
  const noiMargin = revenue === 0 ? 0 : (noi / revenue) * 100
  const netResult = noi + gAndA + depreciation + interest + tax

  return { revenue, opex, capex, gAndA, depreciation, interest, tax, noi, noiMargin, netResult }
}

export async function getDashboardData(filters: KpiFilters) {
  const supabase = await createClient()
  const { year, compareYear, property } = filters

  const fromDate = `${year}-01-01`
  const toDate = `${year}-12-31`

  // Build query
  let query = supabase
    .from('kpi_agg_month')
    .select('*')
    .gte('month', fromDate)
    .lte('month', toDate)

  if (property && property !== 'ALL') {
    query = query.eq('property', property)
  }

  const { data: currentRows = [] } = await query

  // Compare year data
  let compareRows: KpiAggMonth[] = []
  if (compareYear) {
    const fromC = `${compareYear}-01-01`
    const toC   = `${compareYear}-12-31`
    let cq = supabase
      .from('kpi_agg_month')
      .select('*')
      .gte('month', fromC)
      .lte('month', toC)
    if (property && property !== 'ALL') {
      cq = cq.eq('property', property)
    }
    const { data: cr = [] } = await cq
    compareRows = cr as KpiAggMonth[]
  }

  const currentSummary = summarise(currentRows as KpiAggMonth[])
  const compareSummary = compareYear ? summarise(compareRows) : undefined

  // Monthly NOI trend
  const monthlyMap = new Map<string, number>()
  for (const r of currentRows as KpiAggMonth[]) {
    if (r.kpi_group === 'Revenue' || r.kpi_group === 'OPEX') {
      const m = r.month.substring(0, 7)
      monthlyMap.set(m, (monthlyMap.get(m) ?? 0) + Number(r.amount))
    }
  }

  const compareMonthlyMap = new Map<string, number>()
  if (compareYear) {
    for (const r of compareRows) {
      if (r.kpi_group === 'Revenue' || r.kpi_group === 'OPEX') {
        const m = r.month.substring(0, 7)
        compareMonthlyMap.set(m, (compareMonthlyMap.get(m) ?? 0) + Number(r.amount))
      }
    }
  }

  const monthlyNoi: MonthlyNoi[] = Array.from({ length: 12 }, (_, i) => {
    const m = `${year}-${String(i + 1).padStart(2, '0')}`
    const noi = monthlyMap.get(m) ?? 0
    const result: MonthlyNoi = { month: m, noi }
    if (compareYear) {
      const cm = `${compareYear}-${String(i + 1).padStart(2, '0')}`
      result.noiCompare = compareMonthlyMap.get(cm) ?? 0
    }
    return result
  })

  // Property performance lines
  const lineGroups: Record<string, { current: number; compare: number }> = {}
  for (const r of currentRows as KpiAggMonth[]) {
    const key = `${r.kpi_group}|${r.kpi_line}`
    if (!lineGroups[key]) lineGroups[key] = { current: 0, compare: 0 }
    lineGroups[key].current += Number(r.amount)
  }
  for (const r of compareRows) {
    const key = `${r.kpi_group}|${r.kpi_line}`
    if (!lineGroups[key]) lineGroups[key] = { current: 0, compare: 0 }
    lineGroups[key].compare += Number(r.amount)
  }

  // Top expenses (OPEX + G&A, negative amounts = costs)
  const topExpenses: TopItem[] = Object.entries(lineGroups)
    .filter(([key]) => {
      const [grp] = key.split('|')
      return grp === 'OPEX' || grp === 'G&A'
    })
    .map(([key, vals]) => {
      const [kpi_group, kpi_line] = key.split('|')
      return { label: kpi_line.replace(/_/g, ' '), amount: vals.current, kpi_group, kpi_line }
    })
    .sort((a, b) => a.amount - b.amount) // most negative first
    .slice(0, 8)

  // Top revenue lines
  const topRevenue: TopItem[] = Object.entries(lineGroups)
    .filter(([key]) => key.startsWith('Revenue|'))
    .map(([key, vals]) => {
      const [kpi_group, kpi_line] = key.split('|')
      return { label: kpi_line.replace(/_/g, ' '), amount: vals.current, kpi_group, kpi_line }
    })
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 8)

  // Available properties
  const { data: propRows = [] } = await supabase
    .from('kpi_agg_month')
    .select('property')

  const properties = [...new Set((propRows as { property: string }[]).map(r => r.property))].sort()

  return {
    currentSummary,
    compareSummary,
    monthlyNoi,
    topExpenses,
    topRevenue,
    properties,
    year,
    compareYear,
  }
}

export async function getDetailEntries(
  kpiGroup: string,
  kpiLine: string,
  from: string,
  to: string
) {
  const supabase = await createClient()

  const { data, error } = await supabase
    .from('classified_entries')
    .select(`
      entry_id,
      kpi_group,
      kpi_line,
      signed_amount,
      classified_at,
      entries!inner(
        doc_type,
        doc_no,
        date_duup,
        account,
        description,
        partner,
        property_guess,
        currency
      )
    `)
    .eq('kpi_group', kpiGroup)
    .eq('kpi_line', kpiLine)
    .gte('entries.date_duup', from)
    .lte('entries.date_duup', to)
    .order('classified_at', { ascending: false })
    .limit(500)

  if (error) return []
  return data ?? []
}
