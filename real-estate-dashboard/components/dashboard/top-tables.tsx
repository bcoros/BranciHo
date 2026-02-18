'use client'

import Link from 'next/link'
import { formatCurrency, cn } from '@/lib/utils'
import type { TopItem } from '@/lib/dashboard-data'
import { ExternalLink } from 'lucide-react'

interface TopTableProps {
  title: string
  items: TopItem[]
  year: number
  valueLabel?: string
}

export function TopTable({ title, items, year, valueLabel = 'Amount' }: TopTableProps) {
  const maxAbs = Math.max(...items.map(i => Math.abs(i.amount)), 1)

  return (
    <div className="card p-5">
      <h3 className="font-semibold text-slate-900 mb-4">{title}</h3>
      {items.length === 0 ? (
        <p className="text-sm text-slate-400 text-center py-8">No data for selected period</p>
      ) : (
        <div className="space-y-2">
          {items.map((item) => (
            <Link
              key={`${item.kpi_group}-${item.kpi_line}`}
              href={`/dashboard/detail?group=${encodeURIComponent(item.kpi_group)}&line=${encodeURIComponent(item.kpi_line)}&from=${year}-01-01&to=${year}-12-31`}
              className="group flex items-center gap-3 p-2 rounded-lg hover:bg-slate-50 transition-colors"
            >
              <div className="flex-1 min-w-0">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm font-medium text-slate-800 truncate">{item.label}</span>
                  <div className="flex items-center gap-1 ml-2">
                    <span className={cn(
                      'text-sm font-semibold',
                      item.amount < 0 ? 'text-red-600' : 'text-emerald-700'
                    )}>
                      {formatCurrency(item.amount)}
                    </span>
                    <ExternalLink className="w-3 h-3 text-slate-300 group-hover:text-slate-500 transition-colors" />
                  </div>
                </div>
                {/* Progress bar */}
                <div className="h-1 bg-slate-100 rounded-full overflow-hidden">
                  <div
                    className={cn(
                      'h-full rounded-full',
                      item.amount < 0 ? 'bg-red-400' : 'bg-emerald-400'
                    )}
                    style={{ width: `${(Math.abs(item.amount) / maxAbs) * 100}%` }}
                  />
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
