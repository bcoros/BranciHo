import { requireAdmin } from '@/lib/auth'
import { createClient } from '@/lib/supabase/server'
import { UploadForm } from './upload-form'
import { ImportTable } from './import-table'

export default async function UploadsPage() {
  await requireAdmin()
  const supabase = await createClient()

  const { data: imports = [] } = await supabase
    .from('imports')
    .select('*')
    .order('uploaded_at', { ascending: false })
    .limit(50)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-xl font-bold text-slate-900">PDF Imports</h1>
        <p className="text-sm text-slate-500 mt-1">
          Upload TR5 (costs) and TR6 (revenue) PDF exports from Kross Omega accounting system.
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
        {/* Upload Form */}
        <div className="lg:col-span-2 card p-6">
          <h2 className="font-semibold text-slate-900 mb-4">New Import</h2>
          <UploadForm />
        </div>

        {/* Import History */}
        <div className="lg:col-span-3 card overflow-hidden">
          <div className="p-5 border-b border-slate-200">
            <h2 className="font-semibold text-slate-900">Import History</h2>
          </div>
          <ImportTable imports={imports ?? []} />
        </div>
      </div>
    </div>
  )
}
