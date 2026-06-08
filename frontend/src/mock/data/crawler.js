const taskStore = []

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}

export default {
  triggerSiteCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'site', state: 'SUCCESS', result: '站点信息同步完成', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'site', status: 'Task dispatched' } }
  },
  triggerCollectCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'site_index', state: 'SUCCESS', result: '收录统计完成', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'site_index', status: 'Task dispatched' } }
  },
  triggerOrderCrawler: () => {
    const taskId = uuid()
    taskStore.unshift({ task_id: taskId, type: 'order', state: 'SUCCESS', result: '订单同步完成：新增 23 条', created_at: Date.now() })
    return { code: 200, msg: 'success', data: { task_id: taskId, crawler_type: 'order', status: 'Task dispatched' } }
  },
  status: () => ({
    code: 200, msg: 'success',
    data: { task_id: '', state: 'SUCCESS', result: null },
  }),
  tasks: () => ({
    code: 200, msg: 'success',
    data: taskStore.slice(0, 20),
  }),
}
