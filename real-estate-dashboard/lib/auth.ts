import { createClient } from '@/lib/supabase/server'
import { redirect } from 'next/navigation'

export async function requireAuth() {
  const supabase = await createClient()
  const { data: { user }, error } = await supabase.auth.getUser()
  if (error || !user) redirect('/login')
  return user
}

export async function requireAdmin() {
  const user = await requireAuth()
  const role = (user.app_metadata as Record<string, string>)?.role
  if (role !== 'admin') redirect('/dashboard')
  return user
}

export async function getUser() {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  return user
}

export function isAdmin(user: { app_metadata: Record<string, unknown> } | null) {
  if (!user) return false
  return (user.app_metadata as Record<string, string>)?.role === 'admin'
}
