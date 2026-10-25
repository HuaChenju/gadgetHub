export const API_BASE_URL = 'http://localhost:8080/api'

let unauthorizedHandler: ((path: string) => void) | null = null

export function setUnauthorizedHandler(handler: ((path: string) => void) | null) {
  unauthorizedHandler = handler
}

type QueryValue = string | number
export type QueryParams = Record<string, QueryValue | readonly QueryValue[] | null | undefined>

type RequestOptions = {
  method?: 'GET' | 'POST'
  body?: unknown
  query?: QueryParams
}

type ErrorBody = {
  code: string
  message: string
  fieldErrors: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, code: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Не удалось получить ответ сервера. Проверьте соединение.')
    this.name = 'NetworkError'
  }
}

function isErrorBody(value: unknown): value is ErrorBody {
  if (value === null || typeof value !== 'object') return false
  const error = value as Record<string, unknown>
  return typeof error.code === 'string'
    && typeof error.message === 'string'
    && error.fieldErrors !== null
    && typeof error.fieldErrors === 'object'
    && !Array.isArray(error.fieldErrors)
    && Object.values(error.fieldErrors).every(message => typeof message === 'string')
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = new URL(`${API_BASE_URL}/${path.replace(/^\/+/, '')}`)
  for (const [key, value] of Object.entries(options.query ?? {})) {
    if (value === null || value === undefined) continue
    const values = Array.isArray(value) ? value : [value]
    for (const item of values) url.searchParams.append(key, String(item))
  }

  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: string | undefined
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  let response: Response
  try {
    response = await fetch(url, {
      method: options.method ?? 'GET',
      credentials: 'include',
      headers,
      body,
    })
  } catch {
    throw new NetworkError()
  }

  if (response.status === 204) return undefined as T

  let text: string
  try {
    text = await response.text()
  } catch {
    throw new NetworkError()
  }

  let data: unknown
  try {
    data = JSON.parse(text)
  } catch {
    throw new ApiError(
      response.status,
      response.ok ? 'INVALID_RESPONSE' : 'HTTP_ERROR',
      response.ok ? 'Сервер вернул некорректный ответ' : 'Не удалось выполнить запрос. Попробуйте позже',
    )
  }

  if (!response.ok) {
    if (isErrorBody(data)) {
      if (response.status === 401 && data.code === 'UNAUTHORIZED') {
        unauthorizedHandler?.(url.pathname)
      }
      throw new ApiError(response.status, data.code, data.message, data.fieldErrors)
    }
    throw new ApiError(response.status, 'HTTP_ERROR', 'Не удалось выполнить запрос. Попробуйте позже')
  }

  return data as T
}

export const api = {
  get<T>(path: string, query?: QueryParams): Promise<T> {
    return apiRequest<T>(path, { query })
  },
  post<T = void>(path: string, body?: unknown): Promise<T> {
    return apiRequest<T>(path, { method: 'POST', body })
  },
}
