import axios from 'axios'
import { AUTH_TOKEN_KEY } from '../data/keys'

export const useMockData = import.meta.env.VITE_USE_MOCK !== 'false'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败，请稍后重试'
    return Promise.reject(new Error(message))
  },
)

export function unwrapPayload<T>(response: unknown): T {
  const value = response as { data?: unknown; result?: unknown }
  return (value?.result ?? value?.data ?? value) as T
}
