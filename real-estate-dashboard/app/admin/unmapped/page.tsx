import { requireAdmin } from '@/lib/auth'
import { createClient } from '@/lib/supabase/server'
import { UnmappedClient } from './unmapped-client'

export default async function UnmappedPage() {
  await requireAdmin()
  const supabase = await createClient()

  // Get entries that have no classified_entry record
  const { data: entries = [] } = await supabase
    .from('entries')
    .select('*')
    .not('id', 'in',
      supabase.from('classified_entries').select('entry_id')
    )
    .order('date_duup', { ascending: false })
    .limit(200)

  return <UnmappedClient entries={entries ?? []} />
}
