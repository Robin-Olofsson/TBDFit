import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
// Imported before ./index.css specifically so index.css's [data-sonner-toaster][data-sonner-theme='dark']
// override (same specificity as Sonner's own rule of the same selector) wins the cascade tie by
// coming later in the bundled stylesheet — see that rule's own comment.
import 'sonner/dist/styles.css'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth/AuthContext.tsx'
import { queryClient } from './queryClient'

// queryClient is a module-level singleton (see queryClient.ts) — created once, never recreated on
// render, so cached data survives ordinary React Router navigation. See
// docs/architecture/web-server-state-cache.md.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
)
