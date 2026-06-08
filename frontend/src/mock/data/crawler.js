const taskStore = []

// Mock selector templates
const selectorTemplates = [
  { id: 1, name: 'WooCommerce Default', platform: 'woo', titleSelector: "//h1[@class='product_title entry-title']/text() | //h1[contains(@class, 'product-title')]/text()", priceSelector: "//p[@class='price']//bdi/text() | //meta[@itemprop='price']/@content", priceRegex: '[\\d.,]+', descriptionSelector: "//div[@class='woocommerce-product-details__short-description']//text()", imagesSelector: "//meta[@property='og:image']/@content", currency: 'USD', breadcrumbLinksSelector: "//nav[contains(@class, 'woocommerce-breadcrumb')]//a//text()", breadcrumbLastSelector: "//nav[contains(@class, 'woocommerce-breadcrumb')]//a[last()]//text()", siteMapSelector: "//*[local-name()='sitemap']/*[local-name()='loc'][contains(text(), 'product-sitemap')]/text()", isSystem: 1, createdAt: '2026-06-01 10:00:00' },
  { id: 2, name: 'Magnolia Theme', platform: 'woo', titleSelector: '//h1/text()', priceSelector: "//div[contains(@class,'prices')]//div//div//span//span/@content", priceRegex: '[\\d.,]+', descriptionSelector: "//div[contains(@class, 'card-body collapsible-body pdp-feature-body')]/text()", imagesSelector: "//meta[@property='og:image']/@content", currency: 'USD', breadcrumbLinksSelector: "//ol[contains(@class, 'breadcrumb')]//a/text()", breadcrumbLastSelector: "//ol[contains(@class, 'breadcrumb')]//span/text()", siteMapSelector: "//*[local-name()='sitemap']/*[local-name()='loc'][contains(text(), '/sitemap_products_')]/text()", isSystem: 1, createdAt: '2026-06-01 10:01:00' },
  { id: 3, name: 'Shopify Default', platform: 'shopify', titleSelector: null, priceSelector: null, priceRegex: null, descriptionSelector: null, imagesSelector: null, currency: 'USD', breadcrumbLinksSelector: null, breadcrumbLastSelector: null, siteMapSelector: null, isSystem: 1, createdAt: '2026-06-01 10:02:00' },
]

let templateNextId = 4

// Mock site configs
const siteConfigs = [
  { id: 1, domain: 'demo.myshopify.com', type: 'shopify', category: '服饰与配饰', status: 'active', createdBy: 1, createdAt: '2026-06-05 14:00:00', updatedAt: '2026-06-05 14:00:00' },
  { id: 2, domain: 'example.com', type: 'woo', category: '电子产品', status: 'active', createdBy: 1, createdAt: '2026-06-06 09:00:00', updatedAt: '2026-06-06 09:00:00' },
]

const siteTemplateMappings = {
  2: [{ id: 1, siteConfigId: 2, templateId: 1, extraSelectors: null, createdAt: '2026-06-06 09:00:00' }],
}

let configNextId = 3

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

  // ========== Selector Template ==========
  selectorTemplateList: (options) => {
    const platform = options?.platform
    let list = selectorTemplates
    if (platform) list = list.filter(t => t.platform === platform)
    return { code: 200, msg: 'success', data: list }
  },
  selectorTemplateGet: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const tmpl = selectorTemplates.find(t => t.id === id)
    return tmpl ? { code: 200, msg: 'success', data: tmpl } : { code: 404, msg: 'not found', data: null }
  },
  selectorTemplateCreate: (body) => {
    const tmpl = { id: templateNextId++, isSystem: 0, createdAt: new Date().toISOString(), ...body }
    selectorTemplates.unshift(tmpl)
    return { code: 200, msg: 'success', data: tmpl }
  },
  selectorTemplateUpdate: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const idx = selectorTemplates.findIndex(t => t.id === id)
    if (idx >= 0) {
      const body = JSON.parse(options.body || '{}')
      selectorTemplates[idx] = { ...selectorTemplates[idx], ...body }
      return { code: 200, msg: 'success', data: selectorTemplates[idx] }
    }
    return { code: 404, msg: 'not found', data: null }
  },
  selectorTemplateDelete: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)/)[1])
    const idx = selectorTemplates.findIndex(t => t.id === id)
    if (idx >= 0 && selectorTemplates[idx].isSystem !== 1) {
      selectorTemplates.splice(idx, 1)
      return { code: 200, msg: 'success', data: null }
    }
    return { code: 400, msg: 'Cannot delete system template', data: null }
  },
  selectorTemplateClone: (options) => {
    const id = parseInt(options.url.match(/\/admin\/selector\/template\/(\d+)\/clone/)[1])
    const original = selectorTemplates.find(t => t.id === id)
    if (original) {
      const copy = { ...original, id: templateNextId++, name: original.name + ' (copy)', isSystem: 0, createdAt: new Date().toISOString() }
      selectorTemplates.unshift(copy)
      return { code: 200, msg: 'success', data: copy }
    }
    return { code: 404, msg: 'not found', data: null }
  },

  // ========== Site Config ==========
  siteConfigList: () => ({ code: 200, msg: 'success', data: siteConfigs }),
  siteConfigGet: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)/)[1])
    const config = siteConfigs.find(c => c.id === id)
    const mappings = siteTemplateMappings[id] || []
    return config ? { code: 200, msg: 'success', data: { config, mappings } } : { code: 404, msg: 'not found', data: null }
  },
  siteConfigCreate: (body) => {
    const config = { id: configNextId++, status: 'active', createdBy: 1, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString(), ...body.config }
    siteConfigs.unshift(config)
    if (body.mappings?.length > 0) {
      siteTemplateMappings[config.id] = body.mappings.map((m, i) => ({ id: i + 1, siteConfigId: config.id, templateId: m.template_id, extraSelectors: m.extra_selectors || null, createdAt: new Date().toISOString() }))
    }
    return { code: 200, msg: 'success', data: config }
  },
  siteConfigDelete: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)/)[1])
    const idx = siteConfigs.findIndex(c => c.id === id)
    if (idx >= 0) {
      siteConfigs.splice(idx, 1)
      delete siteTemplateMappings[id]
      return { code: 200, msg: 'success', data: null }
    }
    return { code: 404, msg: 'not found', data: null }
  },
  siteConfigCrawl: (options) => {
    const id = parseInt(options.url.match(/\/admin\/crawler\/site-config\/(\d+)\/crawl/)[1])
    const config = siteConfigs.find(c => c.id === id)
    if (config) {
      const taskId = uuid()
      taskStore.unshift({ task_id: taskId, type: 'product_crawl', state: 'SUCCESS', result: `爬取完成: ${config.domain}`, created_at: Date.now() })
      return { code: 200, msg: 'success', data: { task_id: taskId, status: 'Task dispatched' } }
    }
    return { code: 404, msg: 'not found', data: null }
  },
}
