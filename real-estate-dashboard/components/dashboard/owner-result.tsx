import type { KpiSummary } from '@/lib/dashboard-data'
import { formatCurrency, cn } from '@/lib/utils'

interface OwnerResultProps {
  current: KpiSummary
  compare?: KpiSummary
  year: number
  compareYear?: number
}

export function OwnerResult({ current, compare, year, compareYear }: OwnerResultProps) {
  const rows = [
    { label: 'NOI', current: current.noi, compare: compare?.noi },
    { label: 'G&A Costs', current: current.gAndA, compare: compare?.gAndA, indent: true },
    { label: 'Operating Profit', current: current.noi + current.gAndA, compare: compare ? compare.noi + compare.gAndA : undefined, bold: true },
    { label: 'Depreciation', current: current.depreciation, compare: compare?.depreciation, indent: true },
    { label: 'EBIT', current: current.noi + current.gAndA + current.depreciation, compare: compare ? compare.noi + compare.gAndA + compare.depreciation : undefined, bold: true },
    { label: 'Interest Expense', current: current.interest, compare: compare?.interest, indent: true },
    { label: 'EBT', current: current.noi + current.gAndA + current.depreciation + current.interest, compare: compare ? compare.noi + compare.gAndA + compare.depreciation + compare.interest : undefined, bold: true },
    { label: 'Income Tax', current: current.tax, compare: compare?.tax, indent: true },
    { label: 'Net Result', current: current.netResult, compare: compare?.netResult, bold: true, highlight: true },
  ]

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="table-header">
          <tr>
            <th className="table-th">P&L Item</th>
            <th className="table-th text-right">{year}</th>
            {compare && <th className="table-th text-right">{compareYear}</th>}
            {compare && <th className="table-th text-right">Δ</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const d = row.compare !== undefined && row.compare !== 0
              ? ((row.current - row.compare) / Math.abs(row.compare)) * 100
              : null

            return (
              <tr key={row.label} className={cn('table-tr', row.highlight && 'bg-blue-50')}>
                <td className={cn(
                  'table-td',
                  row.indent && 'pl-8',
                  row.bold && 'font-semibold text-slate-900',
                  row.highlight && 'font-bold text-blue-900'
                )}>
                  {row.label}
                </td>
                <td className={cn(
                  'table-td text-right',
                  row.bold ? 'font-semibold' : '',
                  row.current < 0 ? 'text-red-600' : row.current > 0 ? 'text-slate-900' : 'text-slate-500',
                  row.highlight && 'font-bold text-blue-900'
                )}>
                  {formatCurrency(row.current)}
                </td>
                {compare && (
                  <td className="table-td text-right text-slate-500">
                    {row.compare !== undefined ? formatCurrency(row.compare) : '—'}
                  </td>
                )}
                {compare && (
                  <td className={cn(
                    'table-td text-right text-xs',
                    d === null ? 'text-slate-400' : d > 0 ? 'text-emerald-600' : 'text-red-500'
                  )}>
                    {d !== null ? `${d > 0 ? '+' : ''}${d.toFixed(1)}%` : '—'}
                  </td>
                )}
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
