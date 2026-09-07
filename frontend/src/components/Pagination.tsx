interface Props {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

const buttonClass =
  'rounded-md border border-slate-300 px-3 py-1.5 text-sm disabled:cursor-not-allowed disabled:opacity-40 hover:bg-slate-100'

export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) {
    return null
  }

  return (
    <nav className="flex items-center justify-center gap-4" aria-label="Pagination">
      <button type="button" className={buttonClass} disabled={page === 0} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span className="text-sm text-slate-500">
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        className={buttonClass}
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </button>
    </nav>
  )
}
