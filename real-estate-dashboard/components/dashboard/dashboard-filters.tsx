'use client'

import { useRouter, usePathname, useSearchParams } from 'next/navigation'
import { useCallback } from 'react'
import { Building, Calendar, GitCompare } from 'lucide-react'

interface DashboardFiltersProps {
  year: number
  compareYear?: number
  property?: string
  properties: string[]
}

const YEARS = [2023, 2024, 2025, 2026]

export function DashboardFilters({ year, compareYear, property, properties }: DashboardFiltersProps) {
  const router = useRouter()
  const pathname = usePathname()
  const searchParams = useSearchParams()

  const updateParam = useCallback(
    (key: string, value: string | undefined) => {
      const params = new URLSearchParams(searchParams.toString())
      if (value) params.set(key, value)
      else params.delete(key)
      router.push(`${pathname}?${params.toString()}`)
    },
    [pathname, router, searchParams]
  )

  return (
    <div className="flex flex-wrap items-center gap-2">
      {/* Year */}
      <div className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5 text-sm">
        <Calendar className="w-3.5 h-3.5 text-slate-400" />
        <select
          value={year}
          onChange={(e) => updateParam('year', e.target.value)}
          className="bg-transparent text-slate-700 font-medium focus:outline-none cursor-pointer"
        >
          {YEARS.map((y) => (
            <option key={y} value={y}>{y}</option>
          ))}
        </select>
      </div>

      {/* Compare Year */}
      <div className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5 text-sm">
        <GitCompare className="w-3.5 h-3.5 text-slate-400" />
        <select
          value={compareYear ?? ''}
          onChange={(e) => updateParam('compareYear', e.target.value || undefined)}
          className="bg-transparent text-slate-700 focus:outline-none cursor-pointer"
        >
          <option value="">No comparison</option>
          {YEARS.filter((y) => y !== year).map((y) => (
            <option key={y} value={y}>vs {y}</option>
          ))}
        </select>
      </div>

      {/* Property */}
      {properties.length > 0 && (
        <div className="flex items-center gap-1.5 bg-white border border-slate-200 rounded-lg px-2 py-1.5 text-sm">
          <Building className="w-3.5 h-3.5 text-slate-400" />
          <select
            value={property ?? ''}
            onChange={(e) => updateParam('property', e.target.value || undefined)}
            className="bg-transparent text-slate-700 focus:outline-none cursor-pointer"
          >
            <option value="">All Properties</option>
            {properties.map((p) => (
              <option key={p} value={p}>{p}</option>
            ))}
          </select>
        </div>
      )}
    </div>
  )
}
