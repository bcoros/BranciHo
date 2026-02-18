'use client'

import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer
} from 'recharts'
import type { MonthlyNoi } from '@/lib/dashboard-data'
import { formatCurrency } from '@/lib/utils'

interface NoiChartProps {
  data: MonthlyNoi[]
  year: number
  compareYear?: number
}

const MONTH_LABELS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']

function formatTooltip(value: number) {
  return formatCurrency(value)
}

export function NoiChart({ data, year, compareYear }: NoiChartProps) {
  const chartData = data.map((d, i) => ({
    month: MONTH_LABELS[i],
    [String(year)]: d.noi,
    ...(compareYear !== undefined ? { [String(compareYear)]: d.noiCompare ?? 0 } : {}),
  }))

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={chartData} margin={{ top: 5, right: 20, left: 10, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
        <XAxis
          dataKey="month"
          tick={{ fontSize: 11, fill: '#94a3b8' }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          tickFormatter={(v) => `€${(v / 1000).toFixed(0)}k`}
          tick={{ fontSize: 11, fill: '#94a3b8' }}
          axisLine={false}
          tickLine={false}
          width={55}
        />
        <Tooltip
          formatter={formatTooltip}
          contentStyle={{ borderRadius: 8, border: '1px solid #e2e8f0', fontSize: 12 }}
        />
        {compareYear && <Legend wrapperStyle={{ fontSize: 12 }} />}
        <Line
          type="monotone"
          dataKey={String(year)}
          stroke="#3b82f6"
          strokeWidth={2}
          dot={{ r: 3, fill: '#3b82f6' }}
          activeDot={{ r: 5 }}
        />
        {compareYear && (
          <Line
            type="monotone"
            dataKey={String(compareYear)}
            stroke="#94a3b8"
            strokeWidth={2}
            strokeDasharray="5 5"
            dot={{ r: 3, fill: '#94a3b8' }}
          />
        )}
      </LineChart>
    </ResponsiveContainer>
  )
}
