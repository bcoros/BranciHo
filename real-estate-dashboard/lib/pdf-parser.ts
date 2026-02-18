/**
 * PDF Parser for Kross Omega accounting exports
 * Handles TR5 (costs, class 5) and TR6 (revenues, class 6)
 */

import type { Entry, StagingError } from '@/lib/supabase/types'

// Dynamically import pdf-parse (server-side only)
async function getPdfParse() {
  const mod = await import('pdf-parse')
  return mod.default ?? mod
}

export interface ParsedEntry
  extends Omit<Entry, 'id' | 'created_at' | 'import_id'> {
  _rawLine?: string
}

export interface ParseResult {
  entries: ParsedEntry[]
  errors: Array<Omit<StagingError, 'id' | 'created_at' | 'import_id'>>
  periodFrom?: string
  periodTo?: string
}

// Known Slovak property address patterns
const PROPERTY_PATTERNS: Array<[RegExp, string]> = [
  [/Hlavn[áa]\s+\d+/i, 'Hlavná'],
  [/Obchodn[áa]\s+\d+/i, 'Obchodná'],
  [/Mlynsk[áa]\s+\d+/i, 'Mlynská'],
  [/Rooseveltova\s+\d+/i, 'Rooseveltova'],
  [/Pribinova\s+\d+/i, 'Pribinova'],
]

function guessProperty(text: string): string | null {
  for (const [re, name] of PROPERTY_PATTERNS) {
    if (re.test(text)) return name
  }
  return null
}

/**
 * Parse a date string dd.mm.yyyy → 'yyyy-mm-dd'
 */
function parseDate(raw: string): string | null {
  const m = raw.match(/(\d{1,2})\.(\d{1,2})\.(\d{4})/)
  if (!m) return null
  return `${m[3]}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`
}

/**
 * Parse an amount string like '1 234 567,89' or '-1234,56' → number
 */
function parseAmount(raw: string): number | null {
  // Remove thousands separators (spaces, dots used as thousands)
  const cleaned = raw
    .replace(/\s/g, '')
    .replace(/(\d)\.(\d{3})/g, '$1$2') // 1.234.567 → 1234567
    .replace(',', '.')
  const n = parseFloat(cleaned)
  return isNaN(n) ? null : n
}

// ============================================================
// TR6 – Revenue (class 6)
// ============================================================
// TR6 line example:
// OF  1234  15.03.2025  602100  Hlavná 45  - nájom 3/2025  1 200,00
// Fields: docType docNo date account description amount (last token)

const TR6_LINE_RE =
  /^(OF|OD|VF|VD|ID|PP|PD|BD|BV|ZL|ZD|DP|IN|FA|FP)\s+(\S+)\s+(\d{1,2}\.\d{1,2}\.\d{4})\s+(\d{5,6})\s+(.*?)\s+([-\d\s]+[,\.]\d{2})\s*$/

export function parseTR6Lines(
  pageText: string,
  pageNo: number
): { entries: ParsedEntry[]; errors: Array<{ page_no: number; raw_text: string }> } {
  const entries: ParsedEntry[] = []
  const errors: Array<{ page_no: number; raw_text: string }> = []

  const lines = pageText.split('\n').map((l) => l.trim()).filter(Boolean)

  // Skip header/footer lines
  const skipKeywords = [
    'Kniha analytickej', 'Dátum', 'Číslo', 'Popis', 'Suma', 'Strana',
    'Zostatok', 'Obrat', 'Celkom', 'Účet', 'Obdobie', 'Firma',
    'Spolu', 'SPOLU', 'Prenos', 'Konečný zostatok', 'Počiatočný',
  ]

  for (const line of lines) {
    if (skipKeywords.some((kw) => line.includes(kw))) continue
    if (line.length < 15) continue

    const m = line.match(TR6_LINE_RE)
    if (m) {
      const [, docType, docNo, dateRaw, account, description, amountRaw] = m
      const date_duup = parseDate(dateRaw)
      const amount = parseAmount(amountRaw)
      if (amount === null) {
        errors.push({ page_no: pageNo, raw_text: line })
        continue
      }
      entries.push({
        class: 6,
        doc_type: docType,
        doc_no: docNo,
        date_duup: date_duup,
        account: account,
        account_name: null,
        counter_account: null,
        partner: null,
        description: description.trim(),
        amount,
        currency: 'EUR',
        property_guess: guessProperty(description),
        _rawLine: line,
      })
    } else {
      // Try a looser heuristic: find a date, find trailing amount
      const dateM = line.match(/(\d{1,2}\.\d{1,2}\.\d{4})/)
      const amtM = line.match(/([-\d\s]+[,\.]\d{2})\s*$/)
      if (dateM && amtM) {
        const amount = parseAmount(amtM[1])
        if (amount !== null) {
          entries.push({
            class: 6,
            doc_type: null,
            doc_no: null,
            date_duup: parseDate(dateM[1]),
            account: null,
            account_name: null,
            counter_account: null,
            partner: null,
            description: line,
            amount,
            currency: 'EUR',
            property_guess: guessProperty(line),
            _rawLine: line,
          })
          continue
        }
      }
      // Can't parse – log error only if looks like a data line
      if (/\d{1,2}\.\d{1,2}\.\d{4}/.test(line) || /\d{3,}[,\.]\d{2}/.test(line)) {
        errors.push({ page_no: pageNo, raw_text: line })
      }
    }
  }

  return { entries, errors }
}

// ============================================================
// TR5 – Costs (class 5)
// ============================================================
// TR5 line example:
// 5001  15.03.2025  MD  221000  Telekomunikačné služby  - internet 3/2025  250,00
// Fields: docNo date side(MD/D) counterAccount description amount

const TR5_LINE_RE =
  /^(\S+)\s+(\d{1,2}\.\d{1,2}\.\d{4})\s+(MD?|D|Dal|Má dať)\s+(\d{5,6})\s+(.*?)\s+([-\d\s]+[,\.]\d{2})\s*$/i

export function parseTR5Lines(
  pageText: string,
  pageNo: number
): { entries: ParsedEntry[]; errors: Array<{ page_no: number; raw_text: string }> } {
  const entries: ParsedEntry[] = []
  const errors: Array<{ page_no: number; raw_text: string }> = []

  const lines = pageText.split('\n').map((l) => l.trim()).filter(Boolean)

  const skipKeywords = [
    'Kniha analytickej', 'Dátum', 'Číslo', 'Popis', 'Suma', 'Strana',
    'Zostatok', 'Obrat', 'Celkom', 'Účet', 'Obdobie', 'Firma',
    'Spolu', 'SPOLU', 'Prenos', 'Konečný zostatok', 'Počiatočný',
    'Má dať', 'Dal', 'Analytická', 'evidencia',
  ]

  let prevEntry: ParsedEntry | null = null

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (skipKeywords.some((kw) => line.includes(kw))) continue
    if (line.length < 15) continue

    const m = line.match(TR5_LINE_RE)
    if (m) {
      if (prevEntry) entries.push(prevEntry)
      const [, docNo, dateRaw, , counterAccount, description, amountRaw] = m
      const date_duup = parseDate(dateRaw)
      const amount = parseAmount(amountRaw)
      if (amount === null) {
        errors.push({ page_no: pageNo, raw_text: line })
        prevEntry = null
        continue
      }
      prevEntry = {
        class: 5,
        doc_type: null,
        doc_no: docNo,
        date_duup: date_duup,
        account: null,
        account_name: null,
        counter_account: counterAccount,
        partner: null,
        description: description.trim(),
        amount,
        currency: 'EUR',
        property_guess: guessProperty(description),
        _rawLine: line,
      }
    } else {
      // Try to join as continuation of previous description
      if (
        prevEntry &&
        !/\d{1,2}\.\d{1,2}\.\d{4}/.test(line) &&
        !/[-\d\s]+[,\.]\d{2}$/.test(line)
      ) {
        prevEntry.description = `${prevEntry.description} ${line}`.trim()
      } else {
        // Looser parse
        const dateM = line.match(/(\d{1,2}\.\d{1,2}\.\d{4})/)
        const amtM = line.match(/([-\d\s]+[,\.]\d{2})\s*$/)
        if (dateM && amtM) {
          if (prevEntry) entries.push(prevEntry)
          const amount = parseAmount(amtM[1])
          if (amount !== null) {
            prevEntry = {
              class: 5,
              doc_type: null,
              doc_no: null,
              date_duup: parseDate(dateM[1]),
              account: null,
              account_name: null,
              counter_account: null,
              partner: null,
              description: line,
              amount,
              currency: 'EUR',
              property_guess: guessProperty(line),
              _rawLine: line,
            }
          } else {
            prevEntry = null
            errors.push({ page_no: pageNo, raw_text: line })
          }
        } else if (/\d{3,}[,\.]\d{2}/.test(line) || /\d{1,2}\.\d{1,2}\.\d{4}/.test(line)) {
          errors.push({ page_no: pageNo, raw_text: line })
        }
      }
    }
  }
  if (prevEntry) entries.push(prevEntry)

  return { entries, errors }
}

// ============================================================
// Main parse function
// ============================================================
export async function parsePdf(
  buffer: Buffer,
  fileType: 'TR5' | 'TR6' | 'HK'
): Promise<ParseResult> {
  const pdfParse = await getPdfParse()

  const allEntries: ParsedEntry[] = []
  const allErrors: Array<{ page_no: number; raw_text: string }> = []

  // Parse page by page
  let currentPage = 0
  const data = await pdfParse(buffer, {
    pagerender: async (pageData: { getTextContent: () => Promise<{ items: Array<{ str: string; transform: number[] }> }> }) => {
      currentPage++
      const textContent = await pageData.getTextContent()
      const items = textContent.items as Array<{ str: string; transform: number[] }>

      // Sort items by vertical position (y desc) then horizontal (x asc)
      const sorted = [...items].sort((a, b) => {
        const yDiff = b.transform[5] - a.transform[5]
        if (Math.abs(yDiff) > 2) return yDiff
        return a.transform[4] - b.transform[4]
      })

      // Group into lines by y-coordinate
      const lineMap = new Map<number, string[]>()
      for (const item of sorted) {
        const y = Math.round(item.transform[5])
        if (!lineMap.has(y)) lineMap.set(y, [])
        lineMap.get(y)!.push(item.str)
      }

      const pageText = Array.from(lineMap.values())
        .map((words) => words.join(' '))
        .join('\n')

      if (fileType === 'TR6') {
        const { entries, errors } = parseTR6Lines(pageText, currentPage)
        allEntries.push(...entries)
        allErrors.push(...errors)
      } else if (fileType === 'TR5') {
        const { entries, errors } = parseTR5Lines(pageText, currentPage)
        allEntries.push(...entries)
        allErrors.push(...errors)
      }

      return pageText
    },
  })

  // Determine period from entries
  const dates = allEntries
    .filter((e) => e.date_duup)
    .map((e) => e.date_duup as string)
    .sort()

  return {
    entries: allEntries,
    errors: allErrors,
    periodFrom: dates[0],
    periodTo: dates[dates.length - 1],
  }
}
