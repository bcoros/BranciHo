import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { getDetailEntries } from '@/lib/dashboard-data'

export async function GET(request: NextRequest) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return new NextResponse('Unauthorized', { status: 401 })

  const { searchParams } = new URL(request.url)
  const group = searchParams.get('group') ?? ''
  const line  = searchParams.get('line')  ?? ''
  const from  = searchParams.get('from')  ?? ''
  const to    = searchParams.get('to')    ?? ''

  const entries = await getDetailEntries(group, line, from, to)

  const header = 'Date,DocType,DocNo,Description,Property,SignedAmount,Currency\n'
  const rows = entries.map((e) => {
    const entry = (e as { entries: { date_duup: string | null; doc_type: string | null; doc_no: string | null; description: string | null; property_guess: string | null; currency: string } }).entries
    const esc = (v: string | null) => `"${(v ?? '').replace(/"/g, '""')}"`
    return [
      esc(entry?.date_duup),
      esc(entry?.doc_type),
      esc(entry?.doc_no),
      esc(entry?.description),
      esc(entry?.property_guess),
      Number(e.signed_amount).toFixed(2),
      entry?.currency ?? 'EUR',
    ].join(',')
  })

  const csv = header + rows.join('\n')

  return new NextResponse(csv, {
    headers: {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="${group}-${line}-${from}.csv"`,
    },
  })
}
