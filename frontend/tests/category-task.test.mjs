import test from 'node:test'
import assert from 'node:assert/strict'
import { categoryTree } from '../src/data/customCategories.js'
import { taskStatus, taskProgressMessage, taskResult } from '../src/data/taskPresentation.js'
test('shared categories retain leaf identity and propagate disabled parents', () => {
  const rows=[{id:1,parentId:0,name:'工具',enabled:false},{id:2,parentId:1,name:'电钻',enabled:true},{id:3,parentId:0,name:'园艺',enabled:true}]
  assert.deepEqual(categoryTree(rows).map(n=>n.value),['园艺'])
  const historical=categoryTree(rows,true)
  assert.equal(historical[0].children[0].value,'电钻')
  assert.equal(historical[0].children[0].effectiveEnabled,false)
})
test('product failures only expose committed totals and link users to logs', () => {
  const row={type:'product_crawl',status:'FAILED',rowsAffected:123,errorMsg:'SECRET FAILURE',progressMessage:'SECRET FAILURE'}
  assert.equal(taskResult(row),'已入库 123 条')
  assert.equal(taskStatus(row).label,'失败 · 有数据入库')
  assert.equal(taskStatus(row).tone,'danger')
  assert.equal(taskProgressMessage(row),'任务已结束，请查看日志')
  assert.equal(taskStatus({...row,rowsAffected:0}).label,'执行失败')
})
test('terminal states never appear as still running', () => {
  for (const status of ['FAILED','PAUSED','CANCELLED','CANCELED','SUCCESS']) {
    assert.notEqual(taskProgressMessage({status}),'正在执行')
    assert.notEqual(taskStatus({status}).label,status)
  }
  assert.equal(taskStatus({status:'CANCELLED'}).tone,'info')
  assert.equal(taskStatus({status:'RUNNING'}).tone,'primary')
})

test('category API uses the same /admin prefix as the backend routes', async () => {
  const { readFile } = await import('node:fs/promises')
  const source=await readFile(new URL('../src/api/category.js',import.meta.url),'utf8')
  assert.equal((source.match(/\/admin\/custom-categories/g) || []).length,4)
})
