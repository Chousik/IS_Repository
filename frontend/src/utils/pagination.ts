export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export function mapPageModel<T>(page: any, fallbackSize = 0): PagedResult<T> {
  return {
    content: ((page?.content ?? []) as T[]),
    page: page?.page ?? 0,
    size: page?.size ?? fallbackSize,
    totalElements: page?.totalElements ?? 0,
    totalPages: page?.totalPages ?? 0,
  };
}

export function buildSort(sortField?: string, direction?: 'asc' | 'desc'): string | undefined {
  if (!sortField) {
    return undefined;
  }
  const dir = direction ?? 'asc';
  return `${sortField},${dir}`;
}
