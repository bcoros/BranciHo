import { requireAdmin } from '@/lib/auth'
import { Navbar } from '@/components/ui/navbar'

export default async function AdminLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const user = await requireAdmin()

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar userEmail={user.email} isAdmin={true} />
      <main className="max-w-screen-2xl mx-auto px-4 sm:px-6 py-6">
        {children}
      </main>
    </div>
  )
}
