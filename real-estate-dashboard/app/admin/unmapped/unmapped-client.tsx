'use client'

import { useState } from 'react'
import { assignEntry, reclassifyAll } from './actions'
import type { Entry } from '@/lib/supabase/types'
import { formatCurrency, cn } from '@/lib/utils'
import { Save, RefreshCw, Loader2, ChevronDown } from 'lucide-react'
import { useRouter } from 'next/navigation'

const KPI_GROUPS = ['Revenue', 'OPEX', 'G&A', 'CAPEX', 'Depreciation', 'Interest', 'Tax', 'Other']
const KPI_LINES = [
  'Rent', 'Parking', 'Services_To_Tenants', 'Other_Revenue',
  'Insurance', 'Property_Tax', 'Utilities', 'Repairs_Maintenance', 'Services',
  'Bank_Fees', 'Professional_Fees', 'Office_IT', 'Marketing', 'Other',
  'Capex', 'Depreciation', 'Interest', 'Income_Tax',
]

interface UnmappedClientProps {
  entries: Entry[]
}

interface AssignState {
  group: string
  line: string
  sign: number
  saveAsRule: boolean
  textContains: string
  accountPrefix: string
}

export function UnmappedClient({ entries }: UnmappedClientProps) {
  const router = useRouter()
  const [assigning, setAssigning] = useState<Record<string, boolean>>({})
  const [expanded, setExpanded] = useState<Record<string, boolean>>({})
  const [states, setStates] = useState<Record<string, AssignState>>({})
  const [reclassifying, setReclassifying] = useState(false)

  function getState(entry: Entry): AssignState {
    return states[entry.id] ?? {
      group: 'OPEX',
      line: 'Services',
      sign: entry.class === 5 ? -1 : 1,
      saveAsRule: false,
      textContains: entry.description?.slice(0, 30) ?? '',
      accountPrefix: entry.account?.slice(0, 3) ?? '',
    }
  }

  function setState(id: string, update: Partial<AssignState>) {
    setStates((prev) => ({
      ...prev,
      [id]: { ...getState({ id } as Entry), ...prev[id], ...update },
    }))
  }

  async function handleAssign(entry: Entry) {
    const s = getState(entry)
    setAssigning((prev) => ({ ...prev, [entry.id]: true }))
    await assignEntry(
      entry.id,
      s.group,
      s.line,
      entry.amount,
      s.sign,
      s.saveAsRule,
      s.saveAsRule ? s.textContains : undefined,
      s.saveAsRule ? s.accountPrefix : undefined,
      s.saveAsRule ? entry.class : undefined
    )
    setAssigning((prev) => ({ ...prev, [entry.id]: false }))
    router.refresh()
  }

  async function handleReclassify() {
    setReclassifying(true)
    const result = await reclassifyAll()
    setReclassifying(false)
    if (result?.message) alert(result.message)
    router.refresh()
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Unmapped Entries</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {entries.length} entries without a classification rule
          </p>
        </div>
        <button
          onClick={handleReclassify}
          disabled={reclassifying}
          className="btn-secondary"
        >
          {reclassifying
            ? <Loader2 className="w-4 h-4 animate-spin" />
            : <RefreshCw className="w-4 h-4" />}
          Re-classify All
        </button>
      </div>

      {entries.length === 0 ? (
        <div className="card p-16 text-center">
          <p className="text-2xl mb-2">✓</p>
          <p className="font-semibold text-slate-900">All entries are classified!</p>
          <p className="text-sm text-slate-500 mt-1">
            No unmapped items in the queue.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {entries.map((entry) => {
            const s = getState(entry)
            const isExpanded = expanded[entry.id] ?? false
            const isAssigning = assigning[entry.id] ?? false

            return (
              <div key={entry.id} className="card overflow-hidden">
                {/* Header row */}
                <div
                  className="flex items-start gap-4 p-4 cursor-pointer"
                  onClick={() => setExpanded((prev) => ({ ...prev, [entry.id]: !prev[entry.id] }))}
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className={cn(
                        'text-xs font-medium px-2 py-0.5 rounded',
                        entry.class === 5 ? 'bg-blue-100 text-blue-700' : 'bg-emerald-100 text-emerald-700'
                      )}>
                        Class {entry.class}
                      </span>
                      <span className="text-xs text-slate-400">{entry.date_duup}</span>
                      {entry.account && (
                        <span className="text-xs font-mono bg-slate-100 px-1.5 py-0.5 rounded text-slate-600">
                          {entry.account}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-slate-800 truncate">{entry.description ?? '—'}</p>
                    {entry.partner && (
                      <p className="text-xs text-slate-400 mt-0.5">{entry.partner}</p>
                    )}
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={cn(
                      'text-sm font-semibold',
                      entry.amount < 0 ? 'text-red-600' : 'text-emerald-700'
                    )}>
                      {formatCurrency(entry.amount)}
                    </span>
                    <ChevronDown className={cn(
                      'w-4 h-4 text-slate-400 transition-transform',
                      isExpanded && 'rotate-180'
                    )} />
                  </div>
                </div>

                {/* Expanded assign panel */}
                {isExpanded && (
                  <div
                    className="border-t border-slate-100 bg-slate-50 p-4 space-y-3"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                      <div>
                        <label className="label">KPI Group</label>
                        <select
                          value={s.group}
                          onChange={(e) => setState(entry.id, { group: e.target.value })}
                          className="input"
                        >
                          {KPI_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
                        </select>
                      </div>
                      <div>
                        <label className="label">KPI Line</label>
                        <select
                          value={s.line}
                          onChange={(e) => setState(entry.id, { line: e.target.value })}
                          className="input"
                        >
                          {KPI_LINES.map((l) => (
                            <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label className="label">Sign</label>
                        <select
                          value={s.sign}
                          onChange={(e) => setState(entry.id, { sign: Number(e.target.value) })}
                          className="input"
                        >
                          <option value="1">+1 (positive)</option>
                          <option value="-1">-1 (negative)</option>
                        </select>
                      </div>
                      <div className="flex items-end">
                        <label className="flex items-center gap-2 text-sm text-slate-700 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={s.saveAsRule}
                            onChange={(e) => setState(entry.id, { saveAsRule: e.target.checked })}
                            className="rounded"
                          />
                          Save as rule
                        </label>
                      </div>
                    </div>

                    {s.saveAsRule && (
                      <div className="grid grid-cols-2 gap-3">
                        <div>
                          <label className="label">Text Contains (for rule)</label>
                          <input
                            type="text"
                            value={s.textContains}
                            onChange={(e) => setState(entry.id, { textContains: e.target.value })}
                            className="input"
                            placeholder="keyword from description"
                          />
                        </div>
                        <div>
                          <label className="label">Account Prefix (for rule)</label>
                          <input
                            type="text"
                            value={s.accountPrefix}
                            onChange={(e) => setState(entry.id, { accountPrefix: e.target.value })}
                            className="input"
                            placeholder="e.g. 518"
                          />
                        </div>
                      </div>
                    )}

                    <button
                      onClick={() => handleAssign(entry)}
                      disabled={isAssigning}
                      className="btn-primary"
                    >
                      {isAssigning
                        ? <Loader2 className="w-4 h-4 animate-spin" />
                        : <Save className="w-4 h-4" />}
                      {s.saveAsRule ? 'Assign & Save Rule' : 'Assign Once'}
                    </button>
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
