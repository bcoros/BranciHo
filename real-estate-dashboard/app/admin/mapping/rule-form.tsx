'use client'

import { useState, useTransition } from 'react'
import { createRule, updateRule } from './actions'
import type { MappingRule } from '@/lib/supabase/types'
import { cn } from '@/lib/utils'
import { Save, X } from 'lucide-react'

const KPI_GROUPS = ['Revenue', 'OPEX', 'G&A', 'CAPEX', 'Depreciation', 'Interest', 'Tax', 'Other']
const KPI_LINES = [
  'Rent', 'Parking', 'Services_To_Tenants', 'Other_Revenue',
  'Insurance', 'Property_Tax', 'Utilities', 'Repairs_Maintenance', 'Services',
  'Bank_Fees', 'Professional_Fees', 'Office_IT', 'Marketing', 'Other',
  'Capex', 'Depreciation', 'Interest', 'Income_Tax',
]

interface RuleFormProps {
  rule?: MappingRule
  onClose?: () => void
  onSaved?: () => void
}

export function RuleForm({ rule, onClose, onSaved }: RuleFormProps) {
  const isEdit = !!rule
  const [isPending, startTransition] = useTransition()
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(formData: FormData) {
    startTransition(async () => {
      const result = isEdit
        ? await updateRule(rule.id, formData)
        : await createRule(formData)

      if (!result.success) {
        setError(result.message)
      } else {
        setError(null)
        onSaved?.()
        onClose?.()
      }
    })
  }

  return (
    <form action={handleSubmit} className="space-y-4">
      <div className="grid grid-cols-2 gap-3">
        {/* Priority */}
        <div>
          <label className="label">Priority</label>
          <input
            name="priority"
            type="number"
            defaultValue={rule?.priority ?? 100}
            className="input"
            min={1}
            max={9999}
            required
          />
          <p className="text-xs text-slate-400 mt-1">Lower = higher priority</p>
        </div>

        {/* Class */}
        <div>
          <label className="label">Applies To Class</label>
          <select name="applies_to_class" defaultValue={rule?.applies_to_class ?? ''} className="input">
            <option value="">Any</option>
            <option value="5">5 (Costs)</option>
            <option value="6">6 (Revenue)</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {/* Account Prefix */}
        <div>
          <label className="label">Account Prefix</label>
          <input
            name="account_prefix"
            type="text"
            defaultValue={rule?.account_prefix ?? ''}
            className="input"
            placeholder="e.g. 602"
          />
        </div>

        {/* Account Exact */}
        <div>
          <label className="label">Account Exact</label>
          <input
            name="account_exact"
            type="text"
            defaultValue={rule?.account_exact ?? ''}
            className="input"
            placeholder="e.g. 551000"
          />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        {/* Text Contains */}
        <div>
          <label className="label">Description Contains</label>
          <input
            name="text_contains"
            type="text"
            defaultValue={rule?.text_contains ?? ''}
            className="input"
            placeholder="e.g. nájom"
          />
        </div>

        {/* Partner Contains */}
        <div>
          <label className="label">Partner Contains</label>
          <input
            name="partner_contains"
            type="text"
            defaultValue={rule?.partner_contains ?? ''}
            className="input"
            placeholder="e.g. ABC s.r.o."
          />
        </div>
      </div>

      <div className="grid grid-cols-3 gap-3">
        {/* KPI Group */}
        <div>
          <label className="label">KPI Group</label>
          <select name="kpi_group" defaultValue={rule?.kpi_group ?? 'Revenue'} className="input" required>
            {KPI_GROUPS.map((g) => (
              <option key={g} value={g}>{g}</option>
            ))}
          </select>
        </div>

        {/* KPI Line */}
        <div>
          <label className="label">KPI Line</label>
          <select name="kpi_line" defaultValue={rule?.kpi_line ?? 'Other'} className="input" required>
            {KPI_LINES.map((l) => (
              <option key={l} value={l}>{l.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>

        {/* Sign */}
        <div>
          <label className="label">Sign</label>
          <select name="sign" defaultValue={rule?.sign ?? 1} className="input" required>
            <option value="1">+1 (positive)</option>
            <option value="-1">-1 (negative)</option>
          </select>
        </div>
      </div>

      {/* Note */}
      <div>
        <label className="label">Note (optional)</label>
        <input
          name="note"
          type="text"
          defaultValue={rule?.note ?? ''}
          className="input"
          placeholder="Description of this rule"
        />
      </div>

      {/* Active */}
      <div className="flex items-center gap-2">
        <input
          name="is_active"
          type="checkbox"
          defaultChecked={rule?.is_active ?? true}
          className="rounded border-slate-300"
          value="true"
        />
        <label className="text-sm text-slate-700">Active</label>
      </div>

      {error && (
        <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {error}
        </p>
      )}

      <div className="flex items-center gap-2 pt-2">
        <button type="submit" disabled={isPending} className="btn-primary">
          <Save className="w-4 h-4" />
          {isPending ? 'Saving…' : isEdit ? 'Update Rule' : 'Create Rule'}
        </button>
        {onClose && (
          <button type="button" onClick={onClose} className="btn-secondary">
            <X className="w-4 h-4" />
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
