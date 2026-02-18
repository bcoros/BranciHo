'use server'

import { createServiceClient } from '@/lib/supabase/server'
import { requireAdmin } from '@/lib/auth'
import { classifySingleEntry, recomputeKpiAgg } from '@/lib/classifier'
import { revalidatePath } from 'next/cache'

export async function assignEntry(
  entryId: string,
  kpiGroup: string,
  kpiLine: string,
  amount: number,
  sign: number,
  saveAsRule: boolean,
  textContains?: string,
  accountPrefix?: string,
  appliesClass?: number
) {
  await requireAdmin()
  const supabase = await createServiceClient()

  const signedAmount = amount * sign

  let ruleId: string | undefined

  if (saveAsRule) {
    const { data: newRule } = await supabase
      .from('mapping_rules')
      .insert({
        priority: 50,
        is_active: true,
        applies_to_class: appliesClass ?? null,
        account_prefix: accountPrefix ?? null,
        text_contains: textContains ?? null,
        kpi_group: kpiGroup,
        kpi_line: kpiLine,
        sign,
        note: 'Created from unmapped queue',
      })
      .select()
      .single()

    ruleId = newRule?.id
    revalidatePath('/admin/mapping')
  }

  await classifySingleEntry(supabase, entryId, kpiGroup, kpiLine, signedAmount, ruleId)

  revalidatePath('/admin/unmapped')
  revalidatePath('/dashboard')

  return { success: true }
}

export async function reclassifyAll() {
  await requireAdmin()
  const supabase = await createServiceClient()

  // Get all imports and reclassify
  const { data: imports } = await supabase
    .from('imports')
    .select('id')
    .eq('status', 'imported')

  if (!imports) return { success: false }

  const { classifyImport: ci } = await import('@/lib/classifier')
  for (const imp of imports) {
    await ci(supabase, imp.id)
  }

  revalidatePath('/admin/unmapped')
  revalidatePath('/dashboard')
  return { success: true, message: `Reclassified ${imports.length} imports` }
}
