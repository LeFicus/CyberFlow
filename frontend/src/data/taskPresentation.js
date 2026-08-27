export function taskStatus(row) {
  if (row.status === 'FAILED' && row.type === 'product_crawl' && Number(row.rowsAffected)>0) return { label:'失败 · 有数据入库', tone:'danger' }
  const labels={PENDING:['等待执行','info'],RUNNING:['执行中','primary'],SUCCESS:['已完成','success'],FAILED:['执行失败','danger'],PAUSED:['已暂停','warning'],CANCELLED:['已取消','info'],CANCELED:['已取消','info']}
  const [label,tone]=labels[row.status] || ['未知状态','info']
  return {label,tone}
}
export function taskProgressMessage(row) {
  const terminal={FAILED:'任务已结束，请查看日志',PAUSED:'任务已暂停',CANCELLED:'任务已取消',CANCELED:'任务已取消',SUCCESS:'任务已完成'}
  return terminal[row.status] || row.progressMessage || (row.status==='PENDING' ? '等待执行' : '正在执行')
}
export function taskResult(row) {
  if (row.type==='product_crawl') return `已入库 ${Number(row.rowsAffected || 0).toLocaleString()} 条`
  return row.errorMsg || `处理 ${Number(row.rowsAffected || 0).toLocaleString()} 条`
}
