import { onUnmounted, ref } from 'vue'
import { getTaskStatus } from '@/api/crawler'

/** Polls a dispatched crawler task until it reaches a terminal state. */
export function useTaskProgress() {
  const task = ref(null)
  let timer = null

  function stopPolling() {
    if (timer) window.clearTimeout(timer)
    timer = null
  }

  async function poll(taskId) {
    try {
      const res = await getTaskStatus(taskId)
      task.value = { ...res.data, task_id: taskId }
      if (!['SUCCESS', 'FAILED', 'UNKNOWN'].includes(task.value.state)) {
        timer = window.setTimeout(() => poll(taskId), 1500)
      }
    } catch {
      task.value = { task_id: taskId, state: 'FAILED', progress: 0, progress_message: '无法读取任务进度' }
    }
  }

  function track(taskId) {
    stopPolling()
    task.value = { task_id: taskId, state: 'PENDING', progress: 0, progress_message: '任务已下发，等待执行' }
    poll(taskId)
  }

  onUnmounted(stopPolling)
  return { task, track, stopPolling }
}
