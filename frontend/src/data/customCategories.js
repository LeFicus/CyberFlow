export function categoryTree(rows, includeDisabled = false) {
  const nodes = new Map(rows.map(row => [row.id, { ...row, value: row.name, label: row.name, children: [] }]))
  for (const node of nodes.values()) {
    const parent = nodes.get(node.parentId)
    node.effectiveEnabled = node.enabled && (!parent || parent.enabled)
    if (!node.effectiveEnabled) node.label += '（已停用）'
    if (parent) parent.children.push(node)
  }
  const prune = node => ({ ...node, children: node.children.filter(c => includeDisabled || c.effectiveEnabled).map(prune) })
  return [...nodes.values()].filter(n => !n.parentId && (includeDisabled || n.effectiveEnabled)).map(prune)
}
