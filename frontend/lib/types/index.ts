export type Nullable<T> = T | null;

export type ApiSuccessResponse<T> = {
  code: string;
  message: string;
  data: T;
  requestId: string;
  timestamp: string;
};

export type ApiErrorDetail = {
  field: string;
  reason: string;
};

export type ApiErrorResponse = {
  code: string;
  message: string;
  details?: ApiErrorDetail[];
  requestId?: string;
  timestamp?: string;
};

export type PageResponse<T> = {
  list: T[];
  total: number;
  pageNo: number;
  pageSize: number;
  totalPages: number;
  hasNext: boolean;
};
