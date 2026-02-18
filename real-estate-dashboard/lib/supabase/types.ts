export type Database = {
  public: {
    Tables: {
      imports: {
        Row: {
          id: string
          uploaded_by: string | null
          uploaded_at: string
          file_type: 'TR5' | 'TR6' | 'HK'
          source_filename: string
          storage_path: string
          file_hash: string
          period_from: string | null
          period_to: string | null
          rows_parsed: number
          rows_unmapped: number
          status: 'imported' | 'error'
          error_message: string | null
        }
        Insert: Omit<Database['public']['Tables']['imports']['Row'], 'id' | 'uploaded_at'>
        Update: Partial<Database['public']['Tables']['imports']['Insert']>
      }
      entries: {
        Row: {
          id: string
          import_id: string
          class: number
          doc_type: string | null
          doc_no: string | null
          date_duup: string | null
          account: string | null
          account_name: string | null
          counter_account: string | null
          partner: string | null
          description: string | null
          amount: number
          currency: string
          property_guess: string | null
          created_at: string
        }
        Insert: Omit<Database['public']['Tables']['entries']['Row'], 'id' | 'created_at'>
        Update: Partial<Database['public']['Tables']['entries']['Insert']>
      }
      mapping_rules: {
        Row: {
          id: string
          priority: number
          is_active: boolean
          applies_to_class: number | null
          account_prefix: string | null
          account_exact: string | null
          text_contains: string | null
          partner_contains: string | null
          kpi_group: string
          kpi_line: string
          sign: number
          note: string | null
          created_at: string
        }
        Insert: Omit<Database['public']['Tables']['mapping_rules']['Row'], 'id' | 'created_at'>
        Update: Partial<Database['public']['Tables']['mapping_rules']['Insert']>
      }
      classified_entries: {
        Row: {
          entry_id: string
          rule_id: string | null
          kpi_group: string
          kpi_line: string
          signed_amount: number
          classified_at: string
        }
        Insert: Omit<Database['public']['Tables']['classified_entries']['Row'], 'classified_at'>
        Update: Partial<Database['public']['Tables']['classified_entries']['Insert']>
      }
      kpi_agg_month: {
        Row: {
          id: string
          month: string
          property: string
          kpi_group: string
          kpi_line: string
          amount: number
        }
        Insert: Omit<Database['public']['Tables']['kpi_agg_month']['Row'], 'id'>
        Update: Partial<Database['public']['Tables']['kpi_agg_month']['Insert']>
      }
      staging_errors: {
        Row: {
          id: string
          import_id: string
          page_no: number | null
          raw_text: string | null
          created_at: string
        }
        Insert: Omit<Database['public']['Tables']['staging_errors']['Row'], 'id' | 'created_at'>
        Update: Partial<Database['public']['Tables']['staging_errors']['Insert']>
      }
    }
    Views: Record<string, never>
    Functions: Record<string, never>
    Enums: Record<string, never>
  }
}

export type Tables<T extends keyof Database['public']['Tables']> =
  Database['public']['Tables'][T]['Row']

export type Import     = Tables<'imports'>
export type Entry      = Tables<'entries'>
export type MappingRule = Tables<'mapping_rules'>
export type ClassifiedEntry = Tables<'classified_entries'>
export type KpiAggMonth = Tables<'kpi_agg_month'>
export type StagingError = Tables<'staging_errors'>
