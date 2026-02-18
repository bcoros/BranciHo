'use client'

import { formatCurrency, formatPercent, delta, cn } from '@/lib/utils'
import type { KpiSummary } from '@/lib/dashboard-data'
import { TrendingUp, TrendingDown, Minus } from 'lucide-react'

interface KpiTileProps {
  label: string
  value: number
  compareValue?: number
  isCurrency?: boolean
  isPercent?: boolean
  invertDelta?: boolean
}

function DeltaBadge({ pct, inverted }: { pct: number; inverted?: boolean }) {
  const good = inverted ? pct < 0 : pct > 0
  const Icon = pct > 0 ? TrendingUp : TrendingDown
  return (
    <span className={cn(
      'inline-flex items-center gap-0.5 text-xs font-medium',
      good ? 'text-emerald-600' : 'text-red-500'
    )}>
      <Icon className="w-3 h-3" />
      {Math.abs(pct).toFixed(1)}%
    </span>
  )
}

function KpiTile({ label, value, compareValue, isCurrency = true, isPercent, invertDelta }: KpiTileProps) {
  const d = compareValue !== undefined ? delta(value, compareValue) : null

  return (
    <div className="kpi-tile">
      <span className="kpi-label">{label}</span>
      <span className="kpi-value">
        {isPercent
          ? formatPercent(value)
          : isCurrency
          ? formatCurrency(value)
          : value.toFixed(1)}
      </span>
      <div className="flex items-center gap-2 mt-1">
        {d !== null ? (
          <DeltaBadge pct={d} inverted={invertDelta} />
        ) : (
          <span className="text-xs text-slate-400 flex items-center gap-0.5">
            <Minus className="w-3 h-3" /> vs prev year
          </span>
        )}
        {compareValue !== undefined && d !== null && (
          <span className="text-xs text-slate-400">
            vs {formatCurrency(compareValue)}
          </span>
        )}
      </div>
    </div>
  )
}

interface KpiStripProps {
  current: KpiSummary
  compare?: KpiSummary
}

export function KpiStrip({ current, compare }: KpiStripProps) {
  return (
    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4">
      <KpiTile
        label="NOI"
        value={current.noi}
        compareValue={compare?.noi}
      />
      <KpiTile
        label="NOI Margin"
        value={current.noiMargin}
        compareValue={compare?.noiMargin}
        isCurrency={false}
        isPercent
      />
      <KpiTile
        label="OPEX"
        value={current.opex}
        compareValue={compare?.opex}
        invertDelta
      />
      <KpiTile
        label="CAPEX"
        value={current.capex}
        compareValue={compare?.capex}
        invertDelta
      />
      <KpiTile
        label="Net Result"
        value={current.netResult}
        compareValue={compare?.netResult}
      />
    </div>
  )
}
