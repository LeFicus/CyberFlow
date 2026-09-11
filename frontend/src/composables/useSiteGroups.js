import { computed, ref } from 'vue'
import { getSiteGroups } from '@/api/dashboard'

/** Group choices are always derived from the current site_info records. */
export function useSiteGroups() {
  const siteGroups = ref([])
  const groupOptions = computed(() => [
    { label: '全部', value: '' },
    ...siteGroups.value.map(item => ({
      label: `${item.user_group}组`,
      value: item.user_group,
    })),
  ])

  async function loadSiteGroups() {
    try {
      const response = await getSiteGroups()
      siteGroups.value = (response.data || []).filter(item => item?.user_group)
    } catch {
      siteGroups.value = []
    }
    return siteGroups.value
  }

  return { siteGroups, groupOptions, loadSiteGroups }
}
