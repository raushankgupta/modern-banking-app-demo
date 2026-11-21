export interface ApiErrorBody {
  code: string;
  message: string;
  field?: string | null;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  employeeName: string;
  username: string;
}

export interface Session {
  token: string;
  username: string;
  employeeName: string;
}
