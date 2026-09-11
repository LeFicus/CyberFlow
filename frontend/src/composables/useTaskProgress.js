import { onUnmounted, ref } from 'vue'
import { getTaskStatus } from '@/api/crawler'

/** Polls a dispatched crawler task until it reaches a terminal state. */
export function useTaskProgress() {
  const task = ref(null)
  let timer = null
  let pollVersion = 0
  let consecutiveFailures = 0

  function stopPolling() {
    if (timer) window.clearTimeout(timer)
    timer = null
    pollVersion += 1
  }

  async function poll(taskId, version) {
    try {
      const res = await getTaskStatus(taskId)
      if (version !== pollVersion) return
      consecutiveFailures = 0
      task.value = { ...res.data, task_id: taskId }
      if (!['SUCCESS', 'FAILED', 'UNKNOWN', 'CANCELLED', 'CANCELED'].includes(task.value.state)) {
        const delay = task.value.state === 'PAUSED' ? 8000 : 2000
        timer = window.setTimeout(() => poll(taskId, version), delay)
      }
    } catch {
      if (version !== pollVersion) return
      consecutiveFailures += 1
      if (consecutiveFailures < 3) {
        task.value = { ...task.value, task_id: taskId, progress_message: `状态读取失败，正在重试（${consecutiveFailures}/3）` }
        timer = window.setTimeout(() => poll(taskId, version), 3000)
      } else {
        task.value = { ...task.value, task_id: taskId, state: 'UNKNOWN', progress_message: '状态连接已中断，请到任务历史确认结果' }
      }
    }
  }

  function track(taskId) {
    stopPolling()
    consecutiveFailures = 0
    task.value = { task_id: taskId, state: 'PENDING', progress: 0, progress_message: '任务已下发，等待执行' }
    poll(taskId, pollVersion)
  }

  onUnmounted(stopPolling)
  return { task, track, stopPolling }
}
