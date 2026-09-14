const GROUP_COLORS = [
  { backgroundColor: '#ecf5ff', borderColor: '#a9d2ff', color: '#2468a2' },
  { backgroundColor: '#f0f9eb', borderColor: '#b7df9f', color: '#3f7d27' },
  { backgroundColor: '#fdf6ec', borderColor: '#f3c98b', color: '#9a5b13' },
  { backgroundColor: '#f4efff', borderColor: '#cfbaf4', color: '#7048a8' },
  { backgroundColor: '#fef0f0', borderColor: '#f4b5b5', color: '#a13d3d' },
  { backgroundColor: '#eaf8f7', borderColor: '#9bd8d3', color: '#25756f' },
]

/** Give every site group a stable, visually distinct tag color. */
export function groupTagStyle(value) {
  const group = String(value || '').trim().toUpperCase()
  let hash = 0
  for (const char of group) hash = ((hash * 31) + char.codePointAt(0)) >>> 0
  return GROUP_COLORS[hash % GROUP_COLORS.length]
}

export function siteTagLabel(value) {
  return { 0: '单独建站', 1: '批量建站', 2: '复制站' }[Number(value)] || '单独建站'
}

export function siteTagType(value) {
  return { 0: 'success', 1: 'warning', 2: 'info' }[Number(value)] || 'success'
}

/** Format the shared site product-category/category-detail field. */
export function formatSiteCategories(value, fallback = '') {
  const candidate = value ?? fallback
  if (Array.isArray(candidate)) return candidate.filter(Boolean).join('、') || '—'
  if (!candidate) return '—'
  try {
    const parsed = JSON.parse(candidate)
    return Array.isArray(parsed) ? parsed.filter(Boolean).join('、') || '—' : String(parsed)
  } catch {
    return String(candidate)
  }
}
