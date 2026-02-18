import { NextRequest, NextResponse } from 'next/server'
import { createClient } from '@/lib/supabase/server'
import { getDashboardData } from '@/lib/dashboard-data'
import { formatCurrency, formatPercent } from '@/lib/utils'

// Server-side PDF generation using PDFKit
export async function GET(request: NextRequest) {
  const supabase = await createClient()
  const { data: { user } } = await supabase.auth.getUser()
  if (!user) return new NextResponse('Unauthorized', { status: 401 })

  const { searchParams } = new URL(request.url)
  const year = parseInt(searchParams.get('year') ?? String(new Date().getFullYear()))
  const compareYear = searchParams.get('compareYear') ? parseInt(searchParams.get('compareYear')!) : undefined
  const property = searchParams.get('property') ?? undefined

  const data = await getDashboardData({ year, compareYear, property })
  const { currentSummary: s, compareSummary: cs } = data

  // Generate PDF using PDFKit
  const PDFDocument = (await import('pdfkit')).default
  const doc = new PDFDocument({ size: 'A4', margin: 40 })

  const buffers: Buffer[] = []
  doc.on('data', (chunk: Buffer) => buffers.push(chunk))

  await new Promise<void>((resolve) => {
    doc.on('end', resolve)

    // Header
    doc
      .fontSize(20)
      .font('Helvetica-Bold')
      .fillColor('#1e3a8a')
      .text('Executive Real-Estate Dashboard', 40, 40)

    doc
      .fontSize(11)
      .font('Helvetica')
      .fillColor('#64748b')
      .text(`Period: ${year} YTD${compareYear ? ` vs ${compareYear}` : ''}${property ? ` · ${property}` : ''}`, 40, 70)
      .text(`Generated: ${new Date().toLocaleDateString('sk-SK')}`, 40, 85)

    // Divider
    doc.moveTo(40, 105).lineTo(555, 105).strokeColor('#e2e8f0').stroke()

    // KPI Tiles
    doc.fontSize(13).font('Helvetica-Bold').fillColor('#0f172a').text('Key Performance Indicators', 40, 120)

    const kpis = [
      { label: 'NOI', value: formatCurrency(s.noi), cmp: cs ? formatCurrency(cs.noi) : null },
      { label: 'NOI Margin', value: formatPercent(s.noiMargin), cmp: cs ? formatPercent(cs.noiMargin) : null },
      { label: 'Revenue', value: formatCurrency(s.revenue), cmp: cs ? formatCurrency(cs.revenue) : null },
      { label: 'OPEX', value: formatCurrency(s.opex), cmp: cs ? formatCurrency(cs.opex) : null },
      { label: 'CAPEX', value: formatCurrency(s.capex), cmp: cs ? formatCurrency(cs.capex) : null },
    ]

    let kpiX = 40
    const kpiY = 145
    const kpiW = 100

    kpis.forEach(({ label, value, cmp }) => {
      doc.rect(kpiX, kpiY, kpiW - 5, 60).fillColor('#f8fafc').fill()
      doc.fontSize(8).font('Helvetica').fillColor('#64748b').text(label.toUpperCase(), kpiX + 5, kpiY + 8)
      doc.fontSize(12).font('Helvetica-Bold').fillColor('#0f172a').text(value, kpiX + 5, kpiY + 22, { width: kpiW - 10 })
      if (cmp) {
        doc.fontSize(8).font('Helvetica').fillColor('#94a3b8').text(`vs ${cmp}`, kpiX + 5, kpiY + 42)
      }
      kpiX += kpiW + 3
    })

    // P&L Table
    doc.fontSize(13).font('Helvetica-Bold').fillColor('#0f172a').text('Owner P&L', 40, 230)

    const plRows = [
      ['Revenue', formatCurrency(s.revenue), cs ? formatCurrency(cs.revenue) : ''],
      ['OPEX', formatCurrency(s.opex), cs ? formatCurrency(cs.opex) : ''],
      ['NOI', formatCurrency(s.noi), cs ? formatCurrency(cs.noi) : ''],
      ['G&A', formatCurrency(s.gAndA), cs ? formatCurrency(cs.gAndA) : ''],
      ['Depreciation', formatCurrency(s.depreciation), cs ? formatCurrency(cs.depreciation) : ''],
      ['Interest', formatCurrency(s.interest), cs ? formatCurrency(cs.interest) : ''],
      ['Tax', formatCurrency(s.tax), cs ? formatCurrency(cs.tax) : ''],
      ['Net Result', formatCurrency(s.netResult), cs ? formatCurrency(cs.netResult) : ''],
    ]

    let rowY = 255
    const col1 = 40, col2 = 350, col3 = 460

    // Table header
    doc.rect(40, rowY, 515, 18).fillColor('#f1f5f9').fill()
    doc.fontSize(9).font('Helvetica-Bold').fillColor('#475569')
      .text('Line Item', col1 + 5, rowY + 5)
      .text(String(year), col2, rowY + 5)
    if (cs) doc.text(String(compareYear), col3, rowY + 5)
    rowY += 18

    const boldRows = new Set([2, 7])  // NOI, Net Result
    plRows.forEach((row, i) => {
      const isBold = boldRows.has(i)
      if (isBold) doc.rect(40, rowY, 515, 18).fillColor('#eff6ff').fill()
      doc.fontSize(9)
        .font(isBold ? 'Helvetica-Bold' : 'Helvetica')
        .fillColor(isBold ? '#1e40af' : '#334155')
        .text(row[0], col1 + 5, rowY + 5)
        .text(row[1], col2, rowY + 5)
      if (cs && row[2]) doc.text(row[2], col3, rowY + 5)
      rowY += 18
    })

    // Top expenses
    if (data.topExpenses.length > 0) {
      rowY += 15
      doc.fontSize(13).font('Helvetica-Bold').fillColor('#0f172a').text('Top Expenses', 40, rowY)
      rowY += 22

      data.topExpenses.slice(0, 5).forEach((item) => {
        doc.fontSize(9).font('Helvetica').fillColor('#334155')
          .text(item.label, 40 + 5, rowY)
          .text(formatCurrency(item.amount), col2, rowY)
        rowY += 16
      })
    }

    // Footer
    doc.fontSize(8).font('Helvetica').fillColor('#94a3b8')
      .text('Confidential · For internal use only', 40, 780)
      .text(`Executive Real-Estate Dashboard · ${new Date().getFullYear()}`, 350, 780)

    doc.end()
  })

  const pdfBuffer = Buffer.concat(buffers)

  return new NextResponse(pdfBuffer, {
    headers: {
      'Content-Type': 'application/pdf',
      'Content-Disposition': `attachment; filename="dashboard-${year}.pdf"`,
    },
  })
}
