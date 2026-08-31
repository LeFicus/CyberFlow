import assert from 'node:assert/strict'
import { test } from 'node:test'
import { normalizeOrder, parseProductInfo, productImages } from '../src/utils/orderProducts.js'

test('all products and all image fields contribute to the order gallery', () => {
  const order = normalizeOrder({ id: 7, user_group: 'A', product_info: JSON.stringify([
    { name: 'First', images: ['https://img.test/a.jpg', 'https://img.test/b.jpg'], image: 'https://img.test/c.jpg' },
    { name: 'Second', images: [], image_url: 'https://img.test/d.jpg' },
    { name: 'No image', sku: 'SKU-3' },
  ]) })
  assert.equal(order.orderKey, 'A-7')
  assert.equal(order.productInfo.length, 3)
  assert.deepEqual(order.productImages, ['https://img.test/a.jpg', 'https://img.test/b.jpg', 'https://img.test/c.jpg', 'https://img.test/d.jpg'])
  assert.deepEqual(order.productInfo[2].images, [])
  assert.equal(order.productInfo[2].details[0].value, 'SKU-3')
})

test('image collections accept JSON, URL objects and delimited URLs without object strings', () => {
  assert.deepEqual(productImages({
    images: JSON.stringify(['https://img.test/a.jpg', { url: 'https://img.test/b.jpg' }, null, {}]),
    image: ' https://img.test/a.jpg ',
    Images: 'https://img.test/c.jpg, https://img.test/d.jpg\nhttps://img.test/e.jpg',
  }), ['https://img.test/a.jpg', 'https://img.test/b.jpg', 'https://img.test/c.jpg', 'https://img.test/d.jpg', 'https://img.test/e.jpg'])
})

test('expanded details retain every non-image field including zero, false, nested and unknown values', () => {
  const raw = {
    productName: 'A complete product name '.repeat(20), sku: 'SAME-SKU', quantity: 0, price: 0,
    currency: 'USD', specifications: { color: '蓝色', sizes: ['M', 'L'] },
    properties: [{ name: 'engraving', value: 'Line one\nLine two' }],
    customField: false, description: 'First line\nSecond line', note: null, image: 'https://img.test/a.jpg',
  }
  const product = normalizeOrder({ productInfo: [raw] }).productInfo[0]
  assert.equal(product.name, raw.productName)
  const details = Object.fromEntries(product.details.map(field => [field.key, field.value]))
  assert.deepEqual(Object.keys(details), Object.keys(raw).filter(key => !['productName', 'image'].includes(key)))
  assert.equal(details.quantity, '0')
  assert.equal(details.price, '0')
  assert.equal(details.customField, 'false')
  assert.equal(details.description, raw.description)
  assert.equal(details.note, '—')
  assert.deepEqual(JSON.parse(details.specifications), raw.specifications)
  assert.deepEqual(JSON.parse(details.properties), raw.properties)
})

test('duplicate product IDs, SKUs and shared photos do not hide separate product lines', () => {
  const order = normalizeOrder({ productInfo: [
    { id: 1, sku: 'SAME', size: 'M', image: 'https://img.test/a.jpg' },
    { id: 1, sku: 'SAME', size: 'L', image: 'https://img.test/a.jpg' },
  ] })
  assert.equal(order.productInfo.length, 2)
  assert.notEqual(order.productInfo[0].key, order.productInfo[1].key)
  assert.equal(order.productImages.length, 2)
})

test('missing and malformed payloads are safe while single-object payloads remain visible', () => {
  for (const value of [null, undefined, '', '{broken', 'null', 4, [null, false, [], 'text']]) {
    assert.deepEqual(parseProductInfo(value), [])
  }
  assert.equal(normalizeOrder({ product_info: '{"name":"Single"}' }).productInfo[0].name, 'Single')
  assert.equal(normalizeOrder({ productInfo: { sku: 'S1' } }).productInfo[0].name, '商品 1')
  assert.deepEqual(normalizeOrder({ id: 1 }).productImages, [])
})
