export function formatBytes(n: number): string {
  if (!Number.isFinite(n) || n < 0) return '—'
  if (n < 1024) return `${n} B`
  const units = ['KiB', 'MiB', 'GiB']
  let value = n
  let unit = 'B'
  for (const u of units) {
    if (value < 1024) break
    value /= 1024
    unit = u
  }
  return `${value.toFixed(value >= 100 ? 0 : value >= 10 ? 1 : 2)} ${unit}`
}

/** Groups a hex string into byte pairs separated by spaces. */
export function groupHex(hex: string, separator = ' '): string {
  const cleaned = hex.replace(/\s+/g, '')
  const parts: string[] = []
  for (let i = 0; i < cleaned.length; i += 2) {
    parts.push(cleaned.slice(i, i + 2))
  }
  return parts.join(separator)
}

/** Groups a hex string into lines of `bytesPerLine` bytes (monospace dump). */
export function hexLines(hex: string, bytesPerLine = 16): string[] {
  const cleaned = hex.replace(/\s+/g, '')
  const lines: string[] = []
  for (let i = 0; i < cleaned.length; i += bytesPerLine * 2) {
    lines.push(groupHex(cleaned.slice(i, i + bytesPerLine * 2)))
  }
  return lines
}

export function truncateHex(hex: string, maxBytes = 32): string {
  const cleaned = hex.replace(/\s+/g, '')
  if (cleaned.length <= maxBytes * 2) return groupHex(cleaned)
  return `${groupHex(cleaned.slice(0, maxBytes * 2))} … (${cleaned.length / 2} bytes)`
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  })
}
