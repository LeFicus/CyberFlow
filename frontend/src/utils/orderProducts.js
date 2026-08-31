const nameKeys = ['name', 'productName', 'product_name', 'goodsName', 'goods_name', 'title', 'product_title', 'Name']
const imageKeys = ['images', 'image', 'productImage', 'product_image', 'imageUrl', 'image_url', 'thumbnail', 'picture', 'pic', 'Images']
const fieldGroups = [
  ['商品ID', ['id', 'productId', 'product_id', 'goodsId', 'goods_id']],
  ['商品名称', nameKeys],
  ['SKU', ['sku', 'SKU', 'productSku', 'product_sku']],
  ['数量', ['quantity', 'qty', 'count']],
  ['价格', ['price', 'amount']],
  ['单价', ['unitPrice', 'unit_price']],
  ['小计', ['subtotal', 'subTotal', 'sub_total', 'totalPrice', 'total_price']],
  ['币种', ['currency', 'currencyCode', 'currency_code']],
  ['规格', ['spec', 'specs', 'specification', 'specifications', 'variant', 'variantTitle', 'variant_title']],
  ['属性', ['attributes', 'properties', 'options']],
  ['颜色', ['color', 'colour']],
  ['尺码', ['size']],
  ['品牌', ['brand']],
  ['分类', ['category', 'categories']],
  ['重量', ['weight']],
  ['商品描述', ['description', 'desc']],
  ['商品链接', ['url', 'link', 'productUrl', 'product_url']],
]
const fieldLabels = Object.fromEntries(fieldGroups.flatMap(([label, keys]) => keys.map(key => [key, label])))

const isProduct = value => value !== null && typeof value === 'object' && !Array.isArray(value)
const hasValue = value => value !== undefined && value !== null && value !== ''

export function parseProductInfo(value) {
  if (Array.isArray(value)) return value.filter(isProduct)
  if (isProduct(value)) return [value]
  if (typeof value === 'string' && value.trim()) {
    try {
      return parseProductInfo(JSON.parse(value))
    } catch {
      return []
    }
  }
  return []
}

function imageUrls(value) {
  if (Array.isArray(value)) return value.flatMap(imageUrls)
  if (isProduct(value)) return imageUrls(value.url || value.src || value.imageUrl || value.image_url)
  if (typeof value !== 'string' || !value.trim()) return []
  const trimmed = value.trim()
  try {
    return imageUrls(JSON.parse(trimmed))
  } catch {
    // 普通图片 URL 无需 JSON 解析；兼容以换行或逗号分隔的 URL 列表。
    return trimmed.split(/\s*\n\s*|,\s*(?=(?:https?:)?\/\/)/).filter(Boolean)
  }
}

export function productImages(product) {
  return [...new Set(imageKeys.flatMap(key => imageUrls(product[key])))]
}

export function formatProductValue(value) {
  if (!hasValue(value)) return '—'
  if (typeof value === 'object') return JSON.stringify(value, null, 2)
  return String(value)
}

function normalizeProduct(product, index) {
  const nameKey = nameKeys.find(key => hasValue(product[key]))
  return {
    // 相同 SKU 或商品 ID 可以出现在不同订单行中，索引保证每行独立展示。
    key: index,
    name: nameKey ? formatProductValue(product[nameKey]) : `商品 ${index + 1}`,
    images: productImages(product),
    details: Object.entries(product)
      .filter(([key]) => key !== nameKey && !imageKeys.includes(key))
      .map(([key, value]) => ({ key, label: fieldLabels[key] || key, value: formatProductValue(value) })),
  }
}

/** 保留每个商品的所有字段，并汇总全部商品图片供列表和预览使用。 */
export function normalizeOrder(order) {
  const productInfo = parseProductInfo(order.productInfo ?? order.product_info).map(normalizeProduct)
  return {
    ...order,
    orderKey: `${order.user_group || ''}-${order.id}`,
    productInfo,
    productImages: productInfo.flatMap(product => product.images),
  }
}
