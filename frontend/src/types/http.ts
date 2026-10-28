/** Error fields returned by the API client and used by authentication screens. */
export interface HttpRequestError {
  message?: string;
  code?: string;
  response?: {
    status?: number;
    data?: { message?: string; code?: string };
  };
}
