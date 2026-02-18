'use server'

import { createServiceClient } from '@/lib/supabase/server'
import { requireAdmin } from '@/lib/auth'
import { parsePdf } from '@/lib/pdf-parser'
import { classifyImport } from '@/lib/classifier'
import { createHash } from 'crypto'
import { z } from 'zod'
import { revalidatePath } from 'next/cache'

const UploadSchema = z.object({
  fileType: z.enum(['TR5', 'TR6', 'HK']),
})

export async function uploadFile(formData: FormData): Promise<{
  success: boolean
  message: string
  importId?: string
  rowsParsed?: number
  rowsUnmapped?: number
}> {
  const user = await requireAdmin()
  const supabase = await createServiceClient()

  const file = formData.get('file') as File | null
  const rawType = formData.get('fileType') as string

  if (!file || file.size === 0) {
    return { success: false, message: 'No file provided' }
  }

  const parsed = UploadSchema.safeParse({ fileType: rawType })
  if (!parsed.success) {
    return { success: false, message: 'Invalid file type' }
  }
  const { fileType } = parsed.data

  // Read file buffer
  const buffer = Buffer.from(await file.arrayBuffer())
  const fileHash = createHash('sha256').update(buffer).digest('hex')

  // Check duplicate
  const { data: existing } = await supabase
    .from('imports')
    .select('id')
    .eq('file_hash', fileHash)
    .maybeSingle()

  if (existing) {
    return { success: false, message: 'This file has already been imported.' }
  }

  // Upload to Supabase Storage
  const storagePath = `imports/${user.id}/${Date.now()}_${file.name}`
  const { error: storageErr } = await supabase.storage
    .from('imports')
    .upload(storagePath, buffer, { contentType: 'application/pdf' })

  if (storageErr) {
    return { success: false, message: `Storage upload failed: ${storageErr.message}` }
  }

  // Create import record
  const { data: importRecord, error: importErr } = await supabase
    .from('imports')
    .insert({
      uploaded_by: user.id,
      file_type: fileType,
      source_filename: file.name,
      storage_path: storagePath,
      file_hash: fileHash,
      status: 'imported',
    })
    .select()
    .single()

  if (importErr || !importRecord) {
    return { success: false, message: `Failed to create import record: ${importErr?.message}` }
  }

  // Parse PDF
  let parseResult
  try {
    parseResult = await parsePdf(buffer, fileType)
  } catch (err) {
    await supabase
      .from('imports')
      .update({ status: 'error', error_message: String(err) })
      .eq('id', importRecord.id)

    return { success: false, message: `PDF parsing failed: ${String(err)}` }
  }

  // Insert entries in chunks
  const CHUNK = 200
  if (parseResult.entries.length > 0) {
    for (let i = 0; i < parseResult.entries.length; i += CHUNK) {
      const chunk = parseResult.entries.slice(i, i + CHUNK).map((e) => ({
        import_id: importRecord.id,
        class: e.class,
        doc_type: e.doc_type,
        doc_no: e.doc_no,
        date_duup: e.date_duup,
        account: e.account,
        account_name: e.account_name,
        counter_account: e.counter_account,
        partner: e.partner,
        description: e.description,
        amount: e.amount,
        currency: e.currency,
        property_guess: e.property_guess,
      }))

      const { error: entryErr } = await supabase.from('entries').insert(chunk)
      if (entryErr) {
        await supabase
          .from('imports')
          .update({ status: 'error', error_message: entryErr.message })
          .eq('id', importRecord.id)
        return { success: false, message: `Entry insert failed: ${entryErr.message}` }
      }
    }
  }

  // Insert parse errors
  if (parseResult.errors.length > 0) {
    const errChunks = parseResult.errors.map((e) => ({
      import_id: importRecord.id,
      page_no: e.page_no,
      raw_text: e.raw_text,
    }))
    await supabase.from('staging_errors').insert(errChunks)
  }

  // Update import with period info
  await supabase
    .from('imports')
    .update({
      period_from: parseResult.periodFrom ?? null,
      period_to: parseResult.periodTo ?? null,
      rows_parsed: parseResult.entries.length,
    })
    .eq('id', importRecord.id)

  // Classify entries
  let classified = 0, unmapped = 0
  try {
    const result = await classifyImport(supabase, importRecord.id)
    classified = result.classified
    unmapped = result.unmapped
  } catch (err) {
    console.error('Classification error:', err)
  }

  revalidatePath('/admin/uploads')
  revalidatePath('/dashboard')

  return {
    success: true,
    message: `Imported ${parseResult.entries.length} entries (${classified} classified, ${unmapped} unmapped)`,
    importId: importRecord.id,
    rowsParsed: parseResult.entries.length,
    rowsUnmapped: unmapped,
  }
}

export async function deleteImport(importId: string): Promise<{ success: boolean; message: string }> {
  await requireAdmin()
  const supabase = await createServiceClient()

  const { error } = await supabase
    .from('imports')
    .delete()
    .eq('id', importId)

  if (error) return { success: false, message: error.message }

  revalidatePath('/admin/uploads')
  revalidatePath('/dashboard')
  return { success: true, message: 'Import deleted' }
}
