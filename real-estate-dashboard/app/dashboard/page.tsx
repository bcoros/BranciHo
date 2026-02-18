import { Suspense } from 'react'
import { getDashboardData } from '@/lib/dashboard-data'
import { KpiStrip } from '@/components/dashboard/kpi-strip'
import { NoiChart } from '@/components/dashboard/noi-chart'
import { PropertyPerformance } from '@/components/dashboard/property-performance'
import { OwnerResult } from '@/components/dashboard/owner-result'
import { TopTable } from '@/components/dashboard/top-tables'
import { DashboardFilters } from '@/components/dashboard/dashboard-filters'
import { ExportPdfButton } from '@/components/dashboard/export-pdf-button'
import { requireAuth } from '@/lib/auth'

interface PageProps {
  searchParams: Promise<{
    year?: string
    compareYear?: string
    property?: string
  }>
}

export default async function DashboardPage({ searchParams }: PageProps) {
  await requireAuth()
  const params = await searchParams

  const currentYear = new Date().getFullYear()
  const year = parseInt(params.year ?? String(currentYear))
  const compareYear = params.compareYear ? parseInt(params.compareYear) : undefined
  const property = params.property

  const data = await getDashboardData({ year, compareYear, property })

  return (
    <div className="space-y-6" id="dashboard-content">
      {/* Filter Bar */}
      <div className="flex flex-wrap items-center gap-3 justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Executive Dashboard</h1>
          <p className="text-sm text-slate-500">Real-estate portfolio financial performance</p>
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <DashboardFilters
            year={year}
            compareYear={compareYear}
            property={property}
            properties={data.properties}
          />
          <ExportPdfButton year={year} compareYear={compareYear} property={property} />
        </div>
      </div>

      {/* KPI Strip */}
      <Suspense fallback={<div className="h-32 bg-white rounded-xl animate-pulse" />}>
        <KpiStrip
          current={data.currentSummary}
          compare={data.compareSummary}
        />
      </Suspense>

      {/* Main Section: Property Performance + NOI Chart */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        <div className="lg:col-span-3 card p-5">
          <h2 className="font-semibold text-slate-900 mb-4">Property Performance</h2>
          <PropertyPerformance
            current={data.currentSummary}
            compare={data.compareSummary}
            year={year}
            compareYear={compareYear}
          />
        </div>
        <div className="lg:col-span-2 card p-5">
          <div className="flex items-center justify-between mb-4">
            <h2 className="font-semibold text-slate-900">NOI Trend</h2>
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <span className="w-8 h-0.5 bg-blue-500 inline-block rounded" />
              {year}
              {compareYear && (
                <>
                  <span className="w-8 h-0.5 bg-slate-300 inline-block rounded border-dashed" />
                  {compareYear}
                </>
              )}
            </div>
          </div>
          <Suspense fallback={<div className="h-48 animate-pulse bg-slate-50 rounded-lg" />}>
            <NoiChart
              data={data.monthlyNoi}
              year={year}
              compareYear={compareYear}
            />
          </Suspense>
        </div>
      </div>

      {/* Owner Result */}
      <div className="card p-5">
        <h2 className="font-semibold text-slate-900 mb-4">Owner Result (P&L)</h2>
        <OwnerResult
          current={data.currentSummary}
          compare={data.compareSummary}
          year={year}
          compareYear={compareYear}
        />
      </div>

      {/* Top Expenses + Top Revenue */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <TopTable
          title="Top Expenses"
          items={data.topExpenses}
          year={year}
        />
        <TopTable
          title="Top Revenue Lines"
          items={data.topRevenue}
          year={year}
        />
      </div>
    </div>
  )
}
