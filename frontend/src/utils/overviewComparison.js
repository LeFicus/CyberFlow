export function monthOrderComparison(currentValue, previousValue) {
  const current = Number(currentValue || 0)
  const previous = Number(previousValue || 0)
  const change = current - previous
  return {
    current,
    previous,
    change,
    growth: previous ? (change / previous) * 100 : null,
  }
}
