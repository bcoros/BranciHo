'use client'

import { useState } from 'react'
import { RuleForm } from './rule-form'
import { deleteRule, toggleRule, testRule } from './actions'
import type { MappingRule, Entry } from '@/lib/supabase/types'
import { cn } from '@/lib/utils'
import {
  Plus, Pencil, Trash2, FlaskConical, ChevronDown, ChevronUp,
  ToggleLeft, ToggleRight, Loader2
} from 'lucide-react'
import { useRouter } from 'next/navigation'

interface MappingClientProps {
  rules: MappingRule[]
}

export function MappingClient({ rules }: MappingClientProps) {
  const router = useRouter()
  const [showCreate, setShowCreate] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [testingId, setTestingId] = useState<string | null>(null)
  const [testResults, setTestResults] = useState<{ id: string; matches: Entry[] } | null>(null)
  const [deleting, setDeleting] = useState<string | null>(null)
  const [toggling, setToggling] = useState<string | null>(null)

  async function handleDelete(id: string) {
    if (!confirm('Delete this mapping rule?')) return
    setDeleting(id)
    await deleteRule(id)
    setDeleting(null)
    router.refresh()
  }

  async function handleToggle(id: string, current: boolean) {
    setToggling(id)
    await toggleRule(id, !current)
    setToggling(null)
    router.refresh()
  }

  async function handleTest(id: string) {
    setTestingId(id)
    const result = await testRule(id)
    setTestResults({ id, matches: result.matches as Entry[] })
    setTestingId(null)
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Mapping Rules</h1>
          <p className="text-sm text-slate-500 mt-0.5">
            {rules.length} rules · entries are classified by priority (lowest number wins)
          </p>
        </div>
        <button onClick={() => setShowCreate(!showCreate)} className="btn-primary">
          <Plus className="w-4 h-4" />
          New Rule
        </button>
      </div>

      {/* Create Form */}
      {showCreate && (
        <div className="card p-6">
          <h2 className="font-semibold text-slate-900 mb-4">Create Mapping Rule</h2>
          <RuleForm
            onClose={() => setShowCreate(false)}
            onSaved={() => { setShowCreate(false); router.refresh() }}
          />
        </div>
      )}

      {/* Rules Table */}
      <div className="card overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="table-header">
              <tr>
                <th className="table-th w-12">Pri.</th>
                <th className="table-th">Match</th>
                <th className="table-th">Output</th>
                <th className="table-th">Sign</th>
                <th className="table-th">Note</th>
                <th className="table-th w-28">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <>
                  <tr
                    key={rule.id}
                    className={cn('table-tr', !rule.is_active && 'opacity-50')}
                  >
                    <td className="table-td font-mono font-semibold text-blue-700">{rule.priority}</td>
                    <td className="table-td">
                      <div className="flex flex-wrap gap-1">
                        {rule.applies_to_class && (
                          <Chip label={`class=${rule.applies_to_class}`} color="blue" />
                        )}
                        {rule.account_exact && (
                          <Chip label={`acct=${rule.account_exact}`} color="purple" />
                        )}
                        {rule.account_prefix && (
                          <Chip label={`prefix=${rule.account_prefix}*`} color="purple" />
                        )}
                        {rule.text_contains && (
                          <Chip label={`text∋"${rule.text_contains}"`} color="slate" />
                        )}
                        {rule.partner_contains && (
                          <Chip label={`partner∋"${rule.partner_contains}"`} color="slate" />
                        )}
                      </div>
                    </td>
                    <td className="table-td">
                      <span className="font-medium text-slate-900">{rule.kpi_group}</span>
                      <span className="text-slate-400 mx-1">›</span>
                      <span className="text-slate-700">{rule.kpi_line.replace(/_/g, ' ')}</span>
                    </td>
                    <td className="table-td">
                      <span className={cn(
                        'text-xs font-bold px-2 py-0.5 rounded',
                        rule.sign === 1 ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-600'
                      )}>
                        {rule.sign === 1 ? '+1' : '−1'}
                      </span>
                    </td>
                    <td className="table-td text-slate-500 text-xs max-w-[160px] truncate">
                      {rule.note ?? '—'}
                    </td>
                    <td className="table-td">
                      <div className="flex items-center gap-1">
                        {/* Toggle active */}
                        <button
                          onClick={() => handleToggle(rule.id, rule.is_active)}
                          disabled={toggling === rule.id}
                          className="p-1 text-slate-400 hover:text-blue-500 transition-colors"
                          title={rule.is_active ? 'Deactivate' : 'Activate'}
                        >
                          {toggling === rule.id
                            ? <Loader2 className="w-4 h-4 animate-spin" />
                            : rule.is_active
                            ? <ToggleRight className="w-4 h-4 text-emerald-500" />
                            : <ToggleLeft className="w-4 h-4" />}
                        </button>
                        {/* Test */}
                        <button
                          onClick={() => handleTest(rule.id)}
                          disabled={testingId === rule.id}
                          className="p-1 text-slate-400 hover:text-purple-500 transition-colors"
                          title="Test rule"
                        >
                          {testingId === rule.id
                            ? <Loader2 className="w-4 h-4 animate-spin" />
                            : <FlaskConical className="w-4 h-4" />}
                        </button>
                        {/* Edit */}
                        <button
                          onClick={() => setEditingId(editingId === rule.id ? null : rule.id)}
                          className="p-1 text-slate-400 hover:text-slate-700 transition-colors"
                        >
                          {editingId === rule.id
                            ? <ChevronUp className="w-4 h-4" />
                            : <Pencil className="w-4 h-4" />}
                        </button>
                        {/* Delete */}
                        <button
                          onClick={() => handleDelete(rule.id)}
                          disabled={deleting === rule.id}
                          className="p-1 text-slate-400 hover:text-red-500 transition-colors"
                        >
                          {deleting === rule.id
                            ? <Loader2 className="w-4 h-4 animate-spin" />
                            : <Trash2 className="w-4 h-4" />}
                        </button>
                      </div>
                    </td>
                  </tr>

                  {/* Edit form inline */}
                  {editingId === rule.id && (
                    <tr key={`${rule.id}-edit`}>
                      <td colSpan={6} className="p-4 bg-slate-50 border-b border-slate-200">
                        <RuleForm
                          rule={rule}
                          onClose={() => setEditingId(null)}
                          onSaved={() => { setEditingId(null); router.refresh() }}
                        />
                      </td>
                    </tr>
                  )}

                  {/* Test results inline */}
                  {testResults?.id === rule.id && (
                    <tr key={`${rule.id}-test`}>
                      <td colSpan={6} className="p-4 bg-purple-50 border-b border-slate-200">
                        <div className="flex items-center justify-between mb-2">
                          <h4 className="text-sm font-semibold text-purple-900">
                            Test Results ({testResults.matches.length} sample matches)
                          </h4>
                          <button
                            onClick={() => setTestResults(null)}
                            className="text-slate-400 hover:text-slate-600"
                          >
                            <ChevronUp className="w-4 h-4" />
                          </button>
                        </div>
                        {testResults.matches.length === 0 ? (
                          <p className="text-sm text-slate-500">No entries match this rule in the database.</p>
                        ) : (
                          <div className="space-y-1 max-h-48 overflow-y-auto">
                            {testResults.matches.map((m) => (
                              <div key={m.id} className="text-xs bg-white rounded px-3 py-1.5 border border-purple-100">
                                <span className="text-slate-500">{m.date_duup}</span>
                                {' · '}
                                <span className="font-medium">{m.description?.slice(0, 80)}</span>
                                {' · '}
                                <span className="text-slate-700">€{m.amount}</span>
                              </div>
                            ))}
                          </div>
                        )}
                      </td>
                    </tr>
                  )}
                </>
              ))}
              {rules.length === 0 && (
                <tr>
                  <td colSpan={6} className="table-td text-center text-slate-400 py-12">
                    No mapping rules. Create one or run the seed SQL.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function Chip({ label, color }: { label: string; color: 'blue' | 'purple' | 'slate' }) {
  const cls = {
    blue: 'bg-blue-50 text-blue-700 border-blue-200',
    purple: 'bg-purple-50 text-purple-700 border-purple-200',
    slate: 'bg-slate-100 text-slate-600 border-slate-200',
  }[color]
  return (
    <span className={cn('text-xs px-1.5 py-0.5 rounded border font-mono', cls)}>
      {label}
    </span>
  )
}
