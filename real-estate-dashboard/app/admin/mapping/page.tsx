import { requireAdmin } from '@/lib/auth'
import { createClient } from '@/lib/supabase/server'
import { MappingClient } from './mapping-client'

export default async function MappingPage() {
  await requireAdmin()
  const supabase = await createClient()

  const { data: rules = [] } = await supabase
    .from('mapping_rules')
    .select('*')
    .order('priority', { ascending: true })

  return <MappingClient rules={rules ?? []} />
}
