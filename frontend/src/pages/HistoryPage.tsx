import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { clearHistory, getHistory, getQuizStats } from '../api/history'
import { Pagination } from '../components/Pagination'
import { StatCard } from '../components/StatCard'

export function HistoryPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const history = useQuery({ queryKey: ['history', page], queryFn: () => getHistory(page) })
  const stats = useQuery({ queryKey: ['quiz-stats'], queryFn: getQuizStats })

  const clear = useMutation({
    mutationFn: clearHistory,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['history'] }),
  })

  return (
    <div className="space-y-8">
      <section className="space-y-3">
        <h2 className="text-lg font-semibold tracking-tight">Quiz statistics</h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <StatCard label="Answered" value={stats.data?.totalAnswered ?? '—'} />
          <StatCard label="Correct" value={stats.data?.totalCorrect ?? '—'} />
          <StatCard
            label="Accuracy"
            value={stats.data ? `${Math.round(stats.data.accuracy * 100)}%` : '—'}
          />
        </div>
      </section>

      <section className="space-y-3">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-semibold tracking-tight">Search history</h2>
          <button
            type="button"
            onClick={() => clear.mutate()}
            disabled={clear.isPending}
            className="rounded-md border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-100 disabled:opacity-50"
          >
            Clear history
          </button>
        </div>

        {history.isPending && <p className="text-slate-500">Loading…</p>}

        {history.data && history.data.content.length === 0 && (
          <p className="text-slate-500">No searches yet.</p>
        )}

        {history.data && history.data.content.length > 0 && (
          <>
            <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
              <table className="w-full text-sm">
                <thead className="border-b border-slate-200 text-left text-slate-500">
                  <tr>
                    <th className="px-4 py-2 font-medium">Keyword</th>
                    <th className="px-4 py-2 font-medium">Results</th>
                    <th className="px-4 py-2 font-medium">When</th>
                  </tr>
                </thead>
                <tbody>
                  {history.data.content.map((entry) => (
                    <tr key={entry.id} className="border-b border-slate-100 last:border-0">
                      <td className="px-4 py-2 font-medium text-slate-800">{entry.keyword}</td>
                      <td className="px-4 py-2 text-slate-600">{entry.resultCount}</td>
                      <td className="px-4 py-2 text-slate-500">
                        {new Date(entry.searchedAt).toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={history.data.page} totalPages={history.data.totalPages} onChange={setPage} />
          </>
        )}
      </section>
    </div>
  )
}
