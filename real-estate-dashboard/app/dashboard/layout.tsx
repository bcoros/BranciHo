import { requireAuth } from '@/lib/auth'
import { Navbar } from '@/components/ui/navbar'
import { isAdmin } from '@/lib/auth'

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode
}) {
  const user = await requireAuth()

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar
        userEmail={user.email}
        isAdmin={isAdmin(user as Parameters<typeof isAdmin>[0])}
      />
      <main className="max-w-screen-2xl mx-auto px-4 sm:px-6 py-6">
        {children}
      </main>
    </div>
  )
}
