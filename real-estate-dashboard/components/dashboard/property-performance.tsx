import type { KpiSummary } from '@/lib/dashboard-data'
import { formatCurrency, cn } from '@/lib/utils'

interface PropertyPerformanceProps {
  current: KpiSummary
  compare?: KpiSummary
  compareYear?: number
  year: number
}

interface PerfRow {
  label: string
  current: number
  compare?: number
  bold?: boolean
  indent?: boolean
  positive?: boolean
}

function DeltaCell({ current, compare }: { current: number; compare?: number }) {
  if (compare === undefined || compare === 0) return <td className="table-td text-right text-slate-400">—</td>
  const d = ((current - compare) / Math.abs(compare)) * 100
  const good = current > compare
  return (
    <td className={cn('table-td text-right text-xs', good ? 'text-emerald-600' : 'text-red-500')}>
      {d > 0 ? '+' : ''}{d.toFixed(1)}%
    </td>
  )
}

export function PropertyPerformance({ current, compare, compareYear, year }: PropertyPerformanceProps) {
  const rows: PerfRow[] = [
    { label: 'Rental Income', current: current.revenue * 0.8, compare: compare ? compare.revenue * 0.8 : undefined, indent: true },
    { label: 'Parking & Other', current: current.revenue * 0.2, compare: compare ? compare.revenue * 0.2 : undefined, indent: true },
    { label: 'Effective Gross Income', current: current.revenue, compare: compare?.revenue, bold: true },
    { label: 'OPEX', current: current.opex, compare: compare?.opex, indent: true },
    { label: 'NOI', current: current.noi, compare: compare?.noi, bold: true, positive: true },
  ]

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="table-header">
          <tr>
            <th className="table-th">Line Item</th>
            <th className="table-th text-right">{year}</th>
            {compare && <th className="table-th text-right">{compareYear}</th>}
            {compare && <th className="table-th text-right">Δ</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label} className="table-tr">
              <td className={cn('table-td', row.indent && 'pl-8', row.bold && 'font-semibold text-slate-900')}>
                {row.label}
              </td>
              <td className={cn(
                'table-td text-right',
                row.bold ? 'font-semibold text-slate-900' : '',
                row.positive && row.current > 0 ? 'text-emerald-700' : '',
                row.current < 0 ? 'text-red-600' : ''
              )}>
                {formatCurrency(row.current)}
              </td>
              {compare && (
                <td className="table-td text-right text-slate-500">
                  {row.compare !== undefined ? formatCurrency(row.compare) : '—'}
                </td>
              )}
              {compare && <DeltaCell current={row.current} compare={row.compare} />}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
