'use server'

import { createServiceClient } from '@/lib/supabase/server'
import { requireAdmin } from '@/lib/auth'
import { z } from 'zod'
import { revalidatePath } from 'next/cache'

const RuleSchema = z.object({
  priority: z.coerce.number().int().min(1).max(9999),
  is_active: z.coerce.boolean().default(true),
  applies_to_class: z.coerce.number().nullable().default(null),
  account_prefix: z.string().nullable().default(null),
  account_exact: z.string().nullable().default(null),
  text_contains: z.string().nullable().default(null),
  partner_contains: z.string().nullable().default(null),
  kpi_group: z.string().min(1),
  kpi_line: z.string().min(1),
  sign: z.coerce.number().int().refine((v) => v === 1 || v === -1),
  note: z.string().nullable().default(null),
})

function nullifyEmpty<T extends Record<string, unknown>>(obj: T): T {
  const out = { ...obj }
  for (const k of ['account_prefix', 'account_exact', 'text_contains', 'partner_contains', 'note'] as const) {
    if (out[k] === '') (out as Record<string, unknown>)[k] = null
  }
  return out
}

export async function createRule(formData: FormData) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const raw = Object.fromEntries(formData.entries())
  const cleanRaw = {
    ...raw,
    applies_to_class: raw.applies_to_class === '' ? null : raw.applies_to_class,
    is_active: raw.is_active === 'true' || raw.is_active === 'on',
  }

  const parsed = RuleSchema.safeParse(cleanRaw)
  if (!parsed.success) {
    return { success: false, message: parsed.error.errors[0]?.message ?? 'Validation error' }
  }

  const { error } = await supabase
    .from('mapping_rules')
    .insert(nullifyEmpty(parsed.data as Record<string, unknown>) as Parameters<ReturnType<typeof supabase.from>['insert']>[0])

  if (error) return { success: false, message: error.message }

  revalidatePath('/admin/mapping')
  return { success: true, message: 'Rule created' }
}

export async function updateRule(id: string, formData: FormData) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const raw = Object.fromEntries(formData.entries())
  const cleanRaw = {
    ...raw,
    applies_to_class: raw.applies_to_class === '' ? null : raw.applies_to_class,
    is_active: raw.is_active === 'true' || raw.is_active === 'on',
  }

  const parsed = RuleSchema.safeParse(cleanRaw)
  if (!parsed.success) {
    return { success: false, message: parsed.error.errors[0]?.message ?? 'Validation error' }
  }

  const { error } = await supabase
    .from('mapping_rules')
    .update(nullifyEmpty(parsed.data as Record<string, unknown>) as Parameters<ReturnType<typeof supabase.from>['update']>[0])
    .eq('id', id)

  if (error) return { success: false, message: error.message }

  revalidatePath('/admin/mapping')
  return { success: true, message: 'Rule updated' }
}

export async function deleteRule(id: string) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const { error } = await supabase
    .from('mapping_rules')
    .delete()
    .eq('id', id)

  if (error) return { success: false, message: error.message }

  revalidatePath('/admin/mapping')
  return { success: true, message: 'Rule deleted' }
}

export async function toggleRule(id: string, is_active: boolean) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const { error } = await supabase
    .from('mapping_rules')
    .update({ is_active })
    .eq('id', id)

  if (error) return { success: false, message: error.message }

  revalidatePath('/admin/mapping')
  return { success: true }
}

export async function testRule(ruleId: string) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const { data: rule } = await supabase
    .from('mapping_rules')
    .select('*')
    .eq('id', ruleId)
    .single()

  if (!rule) return { matches: [] }

  let query = supabase.from('entries').select('*').limit(20)

  if (rule.applies_to_class) query = query.eq('class', rule.applies_to_class)
  if (rule.account_exact) query = query.eq('account', rule.account_exact)
  if (rule.account_prefix) query = query.ilike('account', `${rule.account_prefix}%`)
  if (rule.text_contains) query = query.ilike('description', `%${rule.text_contains}%`)
  if (rule.partner_contains) query = query.ilike('partner', `%${rule.partner_contains}%`)

  const { data: matches = [] } = await query

  return { matches: matches ?? [] }
}
