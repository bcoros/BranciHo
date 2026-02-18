import { requireAuth } from '@/lib/auth'
import { getDetailEntries } from '@/lib/dashboard-data'
import { formatCurrency } from '@/lib/utils'
import Link from 'next/link'
import { ArrowLeft, Download } from 'lucide-react'

interface PageProps {
  searchParams: Promise<{
    group?: string
    line?: string
    from?: string
    to?: string
  }>
}

export default async function DetailPage({ searchParams }: PageProps) {
  await requireAuth()
  const params = await searchParams

  const { group = '', line = '', from = '', to = '' } = params

  const entries = await getDetailEntries(group, line, from, to)

  const total = entries.reduce((sum, e) => sum + Number(e.signed_amount), 0)

  // CSV export link
  const csvParams = new URLSearchParams({ group, line, from, to })

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link href="/dashboard" className="btn-secondary">
            <ArrowLeft className="w-4 h-4" />
            Back
          </Link>
          <div>
            <h1 className="text-xl font-bold text-slate-900">
              {group} › {line?.replace(/_/g, ' ')}
            </h1>
            <p className="text-sm text-slate-500">
              {from} – {to} · {entries.length} entries · Total: {formatCurrency(total)}
            </p>
          </div>
        </div>
        <a href={`/api/export-csv?${csvParams}`} className="btn-secondary">
          <Download className="w-4 h-4" />
          CSV Export
        </a>
      </div>

      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="table-header">
              <tr>
                <th className="table-th">Date</th>
                <th className="table-th">Doc</th>
                <th className="table-th">Description</th>
                <th className="table-th">Property</th>
                <th className="table-th text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              {entries.length === 0 ? (
                <tr>
                  <td colSpan={5} className="table-td text-center text-slate-400 py-12">
                    No entries found for the selected period and filters.
                  </td>
                </tr>
              ) : (
                entries.map((e) => {
                  const entry = (e as { entries: { date_duup: string | null; doc_type: string | null; doc_no: string | null; description: string | null; property_guess: string | null } }).entries
                  return (
                    <tr key={e.entry_id} className="table-tr">
                      <td className="table-td whitespace-nowrap">{entry?.date_duup ?? '—'}</td>
                      <td className="table-td whitespace-nowrap">
                        <span className="text-xs bg-slate-100 text-slate-600 px-2 py-0.5 rounded">
                          {entry?.doc_type ?? ''} {entry?.doc_no ?? ''}
                        </span>
                      </td>
                      <td className="table-td max-w-xs truncate">{entry?.description ?? '—'}</td>
                      <td className="table-td">
                        {entry?.property_guess ? (
                          <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded">
                            {entry.property_guess}
                          </span>
                        ) : '—'}
                      </td>
                      <td className={`table-td text-right font-medium ${Number(e.signed_amount) < 0 ? 'text-red-600' : 'text-emerald-700'}`}>
                        {formatCurrency(Number(e.signed_amount))}
                      </td>
                    </tr>
                  )
                })
              )}
            </tbody>
            {entries.length > 0 && (
              <tfoot>
                <tr className="bg-slate-50 border-t-2 border-slate-200">
                  <td colSpan={4} className="table-td font-semibold">Total</td>
                  <td className={`table-td text-right font-bold ${total < 0 ? 'text-red-600' : 'text-emerald-700'}`}>
                    {formatCurrency(total)}
                  </td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      </div>
    </div>
  )
}
