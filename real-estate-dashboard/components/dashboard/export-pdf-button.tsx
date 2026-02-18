'use client'

import { useState } from 'react'
import { Download } from 'lucide-react'

interface ExportPdfButtonProps {
  year: number
  compareYear?: number
  property?: string
}

export function ExportPdfButton({ year, compareYear, property }: ExportPdfButtonProps) {
  const [loading, setLoading] = useState(false)

  async function handleExport() {
    setLoading(true)
    try {
      const params = new URLSearchParams({ year: String(year) })
      if (compareYear) params.set('compareYear', String(compareYear))
      if (property) params.set('property', property)

      const response = await fetch(`/api/export-pdf?${params}`)
      if (!response.ok) throw new Error('Export failed')

      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `executive-dashboard-${year}.pdf`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      alert('PDF export failed. Please try again.')
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  return (
    <button
      onClick={handleExport}
      disabled={loading}
      className="btn-secondary"
    >
      <Download className="w-4 h-4" />
      {loading ? 'Generating…' : 'Export PDF'}
    </button>
  )
}
