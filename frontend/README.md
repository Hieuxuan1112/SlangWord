# Frontend

React 19 + TypeScript + Vite SPA for the SlangWord API.

```bash
npm install
npm run dev        # http://localhost:5173, proxies /api to localhost:8081
npm test -- --run
npm run lint
npm run build
```

`npm run dev` expects the API on `localhost:8081` (`docker compose up -d db`, then `mvn spring-boot:run` in `../backend`). Set `VITE_API_BASE_URL` to point somewhere else.

## Layout

| Directory | Contents |
|---|---|
| `src/api/` | axios instance, auth interceptor, one module per resource |
| `src/auth/` | `AuthContext` (the context), `AuthProvider`, `useAuth`, `RequireAuth` route guard |
| `src/components/` | Presentational pieces: `Layout`, `SearchBar`, `WordCard`, `Pagination`, `QuizCard`, `StatCard` |
| `src/pages/` | One component per route |

Server state belongs to TanStack Query; React state is used only for local UI. Components never build URLs — that lives in `src/api/`.
