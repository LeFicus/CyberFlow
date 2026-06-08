import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const breadcrumbs = ref([])

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setBreadcrumbs(items) {
    breadcrumbs.value = items
  }

  return { sidebarCollapsed, breadcrumbs, toggleSidebar, setBreadcrumbs }
})
