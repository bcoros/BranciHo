'use client'

import { useState, useRef } from 'react'
import { uploadFile } from './actions'
import { Upload, FileText, CheckCircle, XCircle, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

type FileType = 'TR5' | 'TR6' | 'HK'

interface UploadResult {
  success: boolean
  message: string
  rowsParsed?: number
  rowsUnmapped?: number
}

export function UploadForm({ onSuccess }: { onSuccess?: () => void }) {
  const [dragging, setDragging] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const [fileType, setFileType] = useState<FileType>('TR6')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<UploadResult | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  function handleDrop(e: React.DragEvent) {
    e.preventDefault()
    setDragging(false)
    const dropped = e.dataTransfer.files[0]
    if (dropped?.type === 'application/pdf') {
      setFile(dropped)
      setResult(null)
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!file) return

    setLoading(true)
    setResult(null)

    const fd = new FormData()
    fd.append('file', file)
    fd.append('fileType', fileType)

    const res = await uploadFile(fd)
    setResult(res)
    setLoading(false)

    if (res.success) {
      setFile(null)
      onSuccess?.()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      {/* Drop Zone */}
      <div
        onDrop={handleDrop}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onClick={() => inputRef.current?.click()}
        className={cn(
          'border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition-colors',
          dragging
            ? 'border-blue-400 bg-blue-50'
            : file
            ? 'border-emerald-300 bg-emerald-50'
            : 'border-slate-300 hover:border-slate-400 bg-slate-50'
        )}
      >
        <input
          ref={inputRef}
          type="file"
          accept=".pdf"
          className="hidden"
          onChange={(e) => {
            const f = e.target.files?.[0]
            if (f) { setFile(f); setResult(null) }
          }}
        />
        {file ? (
          <div className="flex flex-col items-center gap-2">
            <FileText className="w-10 h-10 text-emerald-500" />
            <p className="font-medium text-emerald-700">{file.name}</p>
            <p className="text-sm text-emerald-600">{(file.size / 1024).toFixed(0)} KB</p>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-2">
            <Upload className="w-10 h-10 text-slate-400" />
            <p className="text-slate-600 font-medium">Drop PDF here or click to browse</p>
            <p className="text-sm text-slate-400">Supports TR5, TR6, HK exports from Kross Omega</p>
          </div>
        )}
      </div>

      {/* File Type */}
      <div>
        <label className="label">File Type</label>
        <div className="flex gap-3">
          {(['TR6', 'TR5', 'HK'] as FileType[]).map((t) => (
            <label
              key={t}
              className={cn(
                'flex items-center gap-2 px-4 py-2 rounded-lg border cursor-pointer transition-colors text-sm font-medium',
                fileType === t
                  ? 'border-blue-500 bg-blue-50 text-blue-700'
                  : 'border-slate-200 bg-white text-slate-700 hover:bg-slate-50'
              )}
            >
              <input
                type="radio"
                name="fileType"
                value={t}
                checked={fileType === t}
                onChange={() => setFileType(t)}
                className="hidden"
              />
              {t}
              <span className="text-xs font-normal text-slate-500">
                {t === 'TR6' ? '(Revenue)' : t === 'TR5' ? '(Costs)' : '(HK)'}
              </span>
            </label>
          ))}
        </div>
      </div>

      {/* Result */}
      {result && (
        <div className={cn(
          'flex items-start gap-3 p-4 rounded-lg border text-sm',
          result.success
            ? 'bg-emerald-50 border-emerald-200 text-emerald-800'
            : 'bg-red-50 border-red-200 text-red-800'
        )}>
          {result.success
            ? <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0 mt-0.5" />
            : <XCircle className="w-5 h-5 text-red-500 shrink-0 mt-0.5" />}
          <div>
            <p className="font-medium">{result.message}</p>
            {result.success && result.rowsUnmapped && result.rowsUnmapped > 0 && (
              <p className="mt-1 text-xs">
                {result.rowsUnmapped} entries could not be classified. Visit{' '}
                <a href="/admin/unmapped" className="underline">Unmapped queue</a>.
              </p>
            )}
          </div>
        </div>
      )}

      <button
        type="submit"
        disabled={!file || loading}
        className="btn-primary"
      >
        {loading ? (
          <>
            <Loader2 className="w-4 h-4 animate-spin" />
            Importing…
          </>
        ) : (
          <>
            <Upload className="w-4 h-4" />
            Import PDF
          </>
        )}
      </button>
    </form>
  )
}
