// Shared utility functions
// Add your shared utilities here

export function formatDate(date: Date): string {
  return date.toISOString().split('T')[0];
}

export function isNullOrEmpty(value: string | null | undefined): boolean {
  return value === null || value === undefined || value.trim() === '';
}
