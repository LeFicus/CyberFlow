import request from '@/utils/request'
export const listCategories = () => request.get('/admin/custom-categories')
export const createCategory = data => request.post('/admin/custom-categories', data)
export const updateCategory = (id, data) => request.put(`/admin/custom-categories/${id}`, data)
export const deleteCategory = id => request.delete(`/admin/custom-categories/${id}`)
