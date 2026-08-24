export const SIDEBAR_COUNTS_UPDATED_EVENT = "sidebar-counts:updated";

export function notifySidebarCountsUpdated() {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new Event(SIDEBAR_COUNTS_UPDATED_EVENT));
}
