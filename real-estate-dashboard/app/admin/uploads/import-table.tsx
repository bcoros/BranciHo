'use client'

import { useState } from 'react'
import { deleteImport } from './actions'
import type { Import } from '@/lib/supabase/types'
import { Trash2, AlertCircle, CheckCircle, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

export function ImportTable({ imports }: { imports: Import[] }) {
  const [deleting, setDeleting] = useState<string | null>(null)

  async function handleDelete(id: string) {
    if (!confirm('Delete this import and all its entries? This cannot be undone.')) return
    setDeleting(id)
    await deleteImport(id)
    setDeleting(null)
  }

  if (imports.length === 0) {
    return (
      <div className="p-12 text-center text-sm text-slate-400">
        No imports yet. Upload a PDF to get started.
      </div>
    )
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full">
        <thead className="table-header">
          <tr>
            <th className="table-th">File</th>
            <th className="table-th">Type</th>
            <th className="table-th">Period</th>
            <th className="table-th text-right">Parsed</th>
            <th className="table-th text-right">Unmapped</th>
            <th className="table-th">Status</th>
            <th className="table-th">Date</th>
            <th className="table-th" />
          </tr>
        </thead>
        <tbody>
          {imports.map((imp) => (
            <tr key={imp.id} className="table-tr">
              <td className="table-td max-w-[160px]">
                <span className="truncate block text-xs" title={imp.source_filename}>
                  {imp.source_filename}
                </span>
              </td>
              <td className="table-td">
                <span className={cn(
                  'text-xs font-medium px-2 py-0.5 rounded',
                  imp.file_type === 'TR6' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'
                )}>
                  {imp.file_type}
                </span>
              </td>
              <td className="table-td text-xs text-slate-500">
                {imp.period_from ? (
                  <>{imp.period_from.substring(0, 7)} – {(imp.period_to ?? imp.period_from).substring(0, 7)}</>
                ) : '—'}
              </td>
              <td className="table-td text-right">{imp.rows_parsed}</td>
              <td className="table-td text-right">
                {imp.rows_unmapped > 0 ? (
                  <span className="text-amber-600 font-medium">{imp.rows_unmapped}</span>
                ) : (
                  <span className="text-slate-400">0</span>
                )}
              </td>
              <td className="table-td">
                {imp.status === 'error' ? (
                  <div className="flex items-center gap-1 text-red-600 text-xs" title={imp.error_message ?? ''}>
                    <AlertCircle className="w-3.5 h-3.5" />
                    Error
                  </div>
                ) : (
                  <div className="flex items-center gap-1 text-emerald-600 text-xs">
                    <CheckCircle className="w-3.5 h-3.5" />
                    OK
                  </div>
                )}
              </td>
              <td className="table-td text-xs text-slate-500">
                {new Date(imp.uploaded_at).toLocaleDateString('sk-SK')}
              </td>
              <td className="table-td">
                <button
                  onClick={() => handleDelete(imp.id)}
                  disabled={deleting === imp.id}
                  className="p-1 text-slate-400 hover:text-red-500 transition-colors"
                >
                  {deleting === imp.id
                    ? <Loader2 className="w-4 h-4 animate-spin" />
                    : <Trash2 className="w-4 h-4" />}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
