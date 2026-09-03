import { Outlet, ScrollRestoration } from 'react-router'

export function PublicLayout() {
  return (
    <>
      <Outlet />
      <ScrollRestoration />
    </>
  )
}
